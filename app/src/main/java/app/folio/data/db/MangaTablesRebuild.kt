package app.folio.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Rebuilds the five manga tables in their current layout (database version 7), keeping every row
 * that can be carried over.
 *
 * Folio 1.6.0 to 1.6.3 shipped database version 5 with an earlier layout of these tables, which
 * differs from the version 5 (and 6) that the rest of the history assumes. A phone at version 5
 * may hold either, so this does not trust the version number: it reads the columns each old table
 * actually has and copies what maps onto the new layout. Columns that were renamed are mapped;
 * columns that no longer exist are dropped; new required columns get a default. A table whose
 * rows cannot be filled in (the old download queue, which had no status) starts empty.
 *
 * Only these five tables are touched; books, music and everything else are left as they are.
 */
class MangaTablesRebuild(from: Int) : Migration(from, VERSION) {

    override fun migrate(db: SupportSQLiteDatabase) {
        rebuild(
            object : Sql {
                override fun exec(sql: String) = db.execSQL(sql)
                override fun columns(table: String): Set<String> =
                    db.query("PRAGMA table_info(`$table`)").use { cursor ->
                        val name = cursor.getColumnIndexOrThrow("name")
                        buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
                    }
                override fun tableExists(table: String): Boolean =
                    db.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table))
                        .use { it.moveToFirst() }
            },
            now = System.currentTimeMillis(),
        )
    }

    /** The few database operations the rebuild needs, so it can be tested without Android. */
    interface Sql {
        fun exec(sql: String)
        fun columns(table: String): Set<String>
        fun tableExists(table: String): Boolean
    }

    companion object {
        const val VERSION = 7

        fun rebuild(db: Sql, now: Long) {
            // 1. Copy the old rows aside, as plain tables with no constraints.
            val saved = TABLES.filter { db.tableExists(it.name) }.map { it.name }
            saved.forEach { db.exec("CREATE TEMP TABLE `old_$it` AS SELECT * FROM `$it`") }

            // 2. Drop the old tables, children before parents, so no cascade fires.
            TABLES.reversed().forEach { db.exec("DROP TABLE IF EXISTS `${it.name}`") }

            // 3. Create the current tables, parents first, then copy the rows back.
            TABLES.forEach { table ->
                db.exec(table.createSql)
                table.indexSql.forEach(db::exec)
                if (table.name in saved) copy(db, table, now)
            }
            saved.forEach { db.exec("DROP TABLE `old_$it`") }
        }

        private fun copy(db: Sql, table: Table, now: Long) {
            val old = db.columns("old_${table.name}")
            val select = table.columns.map { column ->
                val source = column.from.firstOrNull { (name, _) -> name in old }
                when {
                    source != null && column.default != null -> "COALESCE(${source.second}, ${column.default})"
                    source != null -> source.second
                    !column.notNull -> "NULL"
                    column.default != null -> column.default
                    // A required column with nothing to fill it from: these rows can't be kept.
                    else -> return
                }
            }.joinToString().replace(NOW, now.toString())
            val names = table.columns.joinToString { "`${it.name}`" }
            val where = table.keepWhere?.let { " WHERE $it" }.orEmpty()
            db.exec("INSERT OR IGNORE INTO `${table.name}` ($names) SELECT $select FROM `old_${table.name}`$where")
        }

        private const val NOW = "{now}"

        private class Column(
            val name: String,
            val notNull: Boolean,
            /** Value for a missing or null old value in a required column. */
            val default: String? = null,
            /** Old column names, each with the expression that reads it, tried in order. */
            renamedFrom: List<Pair<String, String>> = emptyList(),
        ) {
            val from: List<Pair<String, String>> = listOf(name to "`$name`") + renamedFrom
        }

        private class Table(
            val name: String,
            val createSql: String,
            val indexSql: List<String>,
            val columns: List<Column>,
            /** Rows whose parent is gone are left behind instead of breaking a foreign key. */
            val keepWhere: String? = null,
        )

        private fun req(name: String, default: String? = null, vararg renamed: Pair<String, String>) =
            Column(name, notNull = true, default = default, renamedFrom = renamed.toList())

        private fun opt(name: String, vararg renamed: Pair<String, String>) =
            Column(name, notNull = false, renamedFrom = renamed.toList())

        /** Parents before children. The SQL is exactly what Room generates for the entities. */
        private val TABLES = listOf(
            Table(
                name = "manga",
                createSql = "CREATE TABLE IF NOT EXISTS `manga` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT, `artist` TEXT, `description` TEXT, `genre` TEXT, `status` INTEGER NOT NULL, `thumbnailUrl` TEXT, `coverPath` TEXT, `favorite` INTEGER NOT NULL, `initialized` INTEGER NOT NULL, `onlyFetchOnce` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL, `lastChapterFetchAt` INTEGER, `lastReadAt` INTEGER, `memo` TEXT)",
                indexSql = listOf(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_sourceId_url` ON `manga` (`sourceId`, `url`)",
                    "CREATE INDEX IF NOT EXISTS `index_manga_favorite` ON `manga` (`favorite`)",
                    "CREATE INDEX IF NOT EXISTS `index_manga_title` ON `manga` (`title`)",
                ),
                columns = listOf(
                    req("id"), req("sourceId"), req("url"), req("title"),
                    opt("author"), opt("artist"), opt("description"), opt("genre"),
                    req("status", "0"), opt("thumbnailUrl"), opt("coverPath"),
                    req("favorite", "1"), req("initialized", "0"), req("onlyFetchOnce", "0"),
                    req("dateAdded", NOW),
                    opt("lastChapterFetchAt", "lastUpdate" to "NULLIF(`lastUpdate`, 0)"),
                    opt("lastReadAt"), opt("memo"),
                ),
            ),
            Table(
                name = "manga_chapters",
                createSql = "CREATE TABLE IF NOT EXISTS `manga_chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mangaId` INTEGER NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, `scanlator` TEXT, `chapterNumber` REAL NOT NULL, `dateUpload` INTEGER NOT NULL, `dateFetch` INTEGER NOT NULL, `sourceOrder` INTEGER NOT NULL, `read` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, `lastPageRead` INTEGER NOT NULL, `pageCount` INTEGER, `memo` TEXT, FOREIGN KEY(`mangaId`) REFERENCES `manga`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                indexSql = listOf(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_chapters_mangaId_url` ON `manga_chapters` (`mangaId`, `url`)",
                    "CREATE INDEX IF NOT EXISTS `index_manga_chapters_read` ON `manga_chapters` (`read`)",
                ),
                columns = listOf(
                    req("id"), req("mangaId"), req("url"), req("name"), opt("scanlator"),
                    req("chapterNumber", "-1"), req("dateUpload", "0"), req("dateFetch", NOW),
                    req("sourceOrder", "0"), req("read", "0"), req("bookmark", "0"),
                    req("lastPageRead", "0"),
                    opt("pageCount", "pagesCount" to "NULLIF(`pagesCount`, 0)"),
                    opt("memo"),
                ),
                keepWhere = "`mangaId` IN (SELECT `id` FROM `manga`)",
            ),
            Table(
                name = "manga_downloads",
                createSql = "CREATE TABLE IF NOT EXISTS `manga_downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chapterId` INTEGER NOT NULL, `mangaId` INTEGER NOT NULL, `status` TEXT NOT NULL, `path` TEXT, `pageCount` INTEGER, `downloadedPages` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `queuedAt` INTEGER NOT NULL, `completedAt` INTEGER, `error` TEXT, FOREIGN KEY(`chapterId`) REFERENCES `manga_chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`mangaId`) REFERENCES `manga`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                indexSql = listOf(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_downloads_chapterId` ON `manga_downloads` (`chapterId`)",
                    "CREATE INDEX IF NOT EXISTS `index_manga_downloads_mangaId` ON `manga_downloads` (`mangaId`)",
                    "CREATE INDEX IF NOT EXISTS `index_manga_downloads_status` ON `manga_downloads` (`status`)",
                ),
                columns = listOf(
                    req("id"), req("chapterId"), req("mangaId"),
                    req("status"), // no default: a queue row without a status isn't carried over
                    opt("path"), opt("pageCount"), req("downloadedPages", "0"), req("sizeBytes", "0"),
                    req("queuedAt", NOW), opt("completedAt"), opt("error"),
                ),
                keepWhere = "`chapterId` IN (SELECT `id` FROM `manga_chapters`) AND `mangaId` IN (SELECT `id` FROM `manga`)",
            ),
            Table(
                name = "extension_repos",
                createSql = "CREATE TABLE IF NOT EXISTS `extension_repos` (`baseUrl` TEXT NOT NULL, `name` TEXT NOT NULL, `shortName` TEXT, `website` TEXT NOT NULL, `signingKeyFingerprint` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`baseUrl`))",
                indexSql = emptyList(),
                columns = listOf(
                    req("baseUrl"), req("name", "''"), opt("shortName"), req("website", "''"),
                    req("signingKeyFingerprint", "''"), req("addedAt", NOW),
                ),
            ),
            Table(
                name = "trusted_extensions",
                createSql = "CREATE TABLE IF NOT EXISTS `trusted_extensions` (`pkgName` TEXT NOT NULL, `versionCode` INTEGER NOT NULL, `signature` TEXT NOT NULL, `trustedAt` INTEGER NOT NULL, PRIMARY KEY(`pkgName`, `versionCode`, `signature`))",
                indexSql = emptyList(),
                columns = listOf(
                    req("pkgName", null, "packageName" to "`packageName`"),
                    req("versionCode"),
                    req("signature", null, "signatureHash" to "`signatureHash`"),
                    req("trustedAt", NOW),
                ),
            ),
        )
    }
}
