package app.folio.data.db

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the version 7 rebuild on a real SQLite database, from each layout a phone can have:
 * Folio 1.6.0–1.6.3's version 5, and the version 5 and 6 the rest of the history describes.
 * Foreign keys are on and everything runs in one transaction, as in a Room migration.
 */
class MangaTablesRebuildTest {

    private lateinit var connection: Connection
    private val now = 1_700_000_000_000L

    @Before
    fun open() {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec("PRAGMA foreign_keys = ON")
    }

    @After
    fun close() = connection.close()

    @Test
    fun keepsTheLibraryFromFolio163() {
        OLD_163.forEach(::exec)
        exec("INSERT INTO books (id, title) VALUES (1, 'Untouched')")
        exec(
            "INSERT INTO manga (id, sourceId, url, title, artist, author, status, favorite, initialized, " +
                "updateStrategy, dateAdded, lastUpdate, memo) VALUES " +
                "(7, 42, '/m/1', 'One Piece', 'Oda', 'Oda', 1, 1, 1, 'ALWAYS_UPDATE', 1000, 0, NULL)",
        )
        exec(
            "INSERT INTO manga_chapters (id, mangaId, url, name, read, bookmark, lastPageRead, pagesCount, " +
                "chapterNumber, dateUpload, dateFetch, sourceOrder) VALUES " +
                "(70, 7, '/c/1', 'Chapter 1', 1, 0, 11, 12, 1.0, 5, 6, 0), " +
                "(71, 7, '/c/2', 'Chapter 2', 0, 1, 0, 0, 2.0, 5, 6, 1)",
        )
        exec("INSERT INTO manga_downloads (mangaId, chapterId, queuedAt) VALUES (7, 71, 9)")
        exec(
            "INSERT INTO extension_repos (baseUrl, name, website, signingKeyFingerprint) VALUES " +
                "('https://raw.githubusercontent.com/keiyoushi/extensions/repo', 'Keiyoushi', NULL, 'abc'), " +
                "('https://example.org/repo', '', NULL, NULL)",
        )
        exec("INSERT INTO trusted_extensions (packageName, versionCode, signatureHash) VALUES ('eu.x', 3, 'ff')")

        migrate()

        assertCurrentLayout()
        assertEquals(listOf("Untouched"), strings("SELECT title FROM books"))
        assertEquals(
            listOf("7|42|/m/1|One Piece|Oda|Oda|1|1|1|0|1000|null|null|null"),
            rows(
                "SELECT id, sourceId, url, title, author, artist, status, favorite, initialized, onlyFetchOnce, " +
                    "dateAdded, lastChapterFetchAt, lastReadAt, coverPath FROM manga",
            ),
        )
        assertEquals(
            listOf("70|7|1|0|11|12", "71|7|0|1|0|null"),
            rows("SELECT id, mangaId, read, bookmark, lastPageRead, pageCount FROM manga_chapters ORDER BY id"),
        )
        // The old queue had no status, so it starts empty rather than with made-up rows.
        assertEquals(0, count("manga_downloads"))
        assertEquals(
            listOf(
                "https://example.org/repo||||$now",
                "https://raw.githubusercontent.com/keiyoushi/extensions/repo|Keiyoushi||abc|$now",
            ),
            rows("SELECT baseUrl, name, website, signingKeyFingerprint, addedAt FROM extension_repos ORDER BY baseUrl")
                .map { it.replace("null", "") },
        )
        assertEquals(listOf("eu.x|3|ff|$now"), rows("SELECT pkgName, versionCode, signature, trustedAt FROM trusted_extensions"))
    }

    @Test
    fun keepsEverythingFromTheOtherVersion5AndVersion6() {
        for (withMemo in listOf(false, true)) {
            open()
            CURRENT_WITHOUT_MEMO.forEach(::exec)
            if (withMemo) {
                exec("ALTER TABLE manga ADD COLUMN memo TEXT")
                exec("ALTER TABLE manga_chapters ADD COLUMN memo TEXT")
            }
            exec(
                "INSERT INTO manga (id, sourceId, url, title, status, favorite, initialized, onlyFetchOnce, dateAdded, " +
                    "lastChapterFetchAt, lastReadAt, coverPath) VALUES (1, 2, '/m', 'T', 0, 1, 1, 1, 10, 20, 30, '/c.jpg')",
            )
            exec(
                "INSERT INTO manga_chapters (id, mangaId, url, name, chapterNumber, dateUpload, dateFetch, sourceOrder, " +
                    "read, bookmark, lastPageRead, pageCount) VALUES (5, 1, '/c', 'C', 1.5, 1, 2, 0, 0, 0, 3, 9)",
            )
            exec(
                "INSERT INTO manga_downloads (chapterId, mangaId, status, path, pageCount, downloadedPages, sizeBytes, " +
                    "queuedAt, completedAt) VALUES (5, 1, 'DOWNLOADED', '/d/5.cbz', 9, 9, 1234, 3, 4)",
            )
            exec("INSERT INTO extension_repos VALUES ('https://r', 'R', 'RR', 'https://w', 'key', 99)")
            exec("INSERT INTO trusted_extensions VALUES ('p', 1, 's', 98)")

            migrate()

            assertCurrentLayout()
            assertEquals(listOf("1|1|20|30|/c.jpg"), rows("SELECT id, onlyFetchOnce, lastChapterFetchAt, lastReadAt, coverPath FROM manga"))
            assertEquals(listOf("5|1.5|9"), rows("SELECT id, chapterNumber, pageCount FROM manga_chapters"))
            assertEquals(listOf("DOWNLOADED|/d/5.cbz|1234|4"), rows("SELECT status, path, sizeBytes, completedAt FROM manga_downloads"))
            assertEquals(listOf("https://r|R|RR|https://w|key|99"), rows("SELECT * FROM extension_repos"))
            assertEquals(listOf("p|1|s|98"), rows("SELECT * FROM trusted_extensions"))
            close()
        }
    }

    @Test
    fun deletingAMangaStillCascadesAfterTheRebuild() {
        OLD_163.forEach(::exec)
        exec(
            "INSERT INTO manga (id, sourceId, url, title, status, favorite, initialized, updateStrategy, dateAdded, lastUpdate) " +
                "VALUES (1, 1, '/m', 'T', 0, 1, 0, 'ALWAYS_UPDATE', 1, 0)",
        )
        exec(
            "INSERT INTO manga_chapters (mangaId, url, name, read, bookmark, lastPageRead, pagesCount, chapterNumber, " +
                "dateUpload, dateFetch, sourceOrder) VALUES (1, '/c', 'C', 0, 0, 0, 0, 1, 0, 0, 0)",
        )
        migrate()
        exec("DELETE FROM manga")
        assertEquals(0, count("manga_chapters"))
    }

    private fun migrate() {
        connection.autoCommit = false
        MangaTablesRebuild.rebuild(
            object : MangaTablesRebuild.Sql {
                override fun exec(sql: String) = this@MangaTablesRebuildTest.exec(sql)
                override fun columns(table: String) = strings("PRAGMA table_info(`$table`)", column = 2).toSet()
                override fun tableExists(table: String) =
                    strings("SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'").isNotEmpty()
            },
            now,
        )
        connection.commit()
        connection.autoCommit = true
        assertNull("foreign key violations", rows("PRAGMA foreign_key_check").firstOrNull())
        assertEquals(emptyList<String>(), strings("SELECT name FROM sqlite_temp_master WHERE type = 'table'"))
    }

    /** Columns, keys and indexes match a database created fresh at version 7. */
    private fun assertCurrentLayout() {
        val fresh = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            CURRENT_WITHOUT_MEMO.forEach { fresh.createStatement().use { s -> s.execute(it) } }
            fresh.createStatement().use { it.execute("ALTER TABLE manga ADD COLUMN memo TEXT") }
            fresh.createStatement().use { it.execute("ALTER TABLE manga_chapters ADD COLUMN memo TEXT") }
            for (table in listOf("manga", "manga_chapters", "manga_downloads", "extension_repos", "trusted_extensions")) {
                for (pragma in listOf("table_info", "foreign_key_list", "index_list")) {
                    val query = "PRAGMA $pragma(`$table`)"
                    assertEquals("$query", rows(query, fresh).sorted(), rows(query).sorted())
                }
            }
        } finally {
            fresh.close()
        }
    }

    private fun exec(sql: String) {
        connection.createStatement().use { it.execute(sql) }
    }

    private fun count(table: String) = strings("SELECT COUNT(*) FROM `$table`").single().toInt()

    private fun strings(sql: String, column: Int = 1): List<String> =
        connection.createStatement().use { s ->
            s.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(rs.getString(column)) } }
        }

    private fun rows(sql: String, on: Connection = connection): List<String> =
        on.createStatement().use { s ->
            s.executeQuery(sql).use { rs ->
                val n = rs.metaData.columnCount
                buildList { while (rs.next()) add((1..n).joinToString("|") { rs.getString(it).toString() }) }
            }
        }

    private companion object {
        /** The manga tables as Folio 1.6.0–1.6.3 created them, plus one unrelated table. */
        val OLD_163 = listOf(
            "CREATE TABLE books (id INTEGER PRIMARY KEY, title TEXT)",
            "CREATE TABLE IF NOT EXISTS `manga` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `artist` TEXT, `author` TEXT, `description` TEXT, `genre` TEXT, `status` INTEGER NOT NULL, `thumbnailUrl` TEXT, `favorite` INTEGER NOT NULL, `initialized` INTEGER NOT NULL, `updateStrategy` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL, `lastUpdate` INTEGER NOT NULL, `memo` TEXT)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_sourceId_url` ON `manga` (`sourceId`, `url`)",
            "CREATE INDEX IF NOT EXISTS `index_manga_favorite` ON `manga` (`favorite`)",
            "CREATE TABLE IF NOT EXISTS `manga_chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mangaId` INTEGER NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, `scanlator` TEXT, `read` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, `lastPageRead` INTEGER NOT NULL, `pagesCount` INTEGER NOT NULL, `chapterNumber` REAL NOT NULL, `dateUpload` INTEGER NOT NULL, `dateFetch` INTEGER NOT NULL, `sourceOrder` INTEGER NOT NULL, `memo` TEXT, FOREIGN KEY(`mangaId`) REFERENCES `manga`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_chapters_mangaId_url` ON `manga_chapters` (`mangaId`, `url`)",
            "CREATE INDEX IF NOT EXISTS `index_manga_chapters_mangaId` ON `manga_chapters` (`mangaId`)",
            "CREATE TABLE IF NOT EXISTS `manga_downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mangaId` INTEGER NOT NULL, `chapterId` INTEGER NOT NULL, `queuedAt` INTEGER NOT NULL, FOREIGN KEY(`chapterId`) REFERENCES `manga_chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_downloads_chapterId` ON `manga_downloads` (`chapterId`)",
            "CREATE INDEX IF NOT EXISTS `index_manga_downloads_mangaId` ON `manga_downloads` (`mangaId`)",
            "CREATE TABLE IF NOT EXISTS `extension_repos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `baseUrl` TEXT NOT NULL, `name` TEXT NOT NULL, `website` TEXT, `signingKeyFingerprint` TEXT)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_extension_repos_baseUrl` ON `extension_repos` (`baseUrl`)",
            "CREATE TABLE IF NOT EXISTS `trusted_extensions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packageName` TEXT NOT NULL, `versionCode` INTEGER NOT NULL, `signatureHash` TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_trusted_extensions_packageName_versionCode_signatureHash` ON `trusted_extensions` (`packageName`, `versionCode`, `signatureHash`)",
        )

        /** Version 5 as in app/schemas/.../5.json; version 6 adds the two memo columns. */
        val CURRENT_WITHOUT_MEMO = listOf(
            "CREATE TABLE IF NOT EXISTS `manga` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sourceId` INTEGER NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, `author` TEXT, `artist` TEXT, `description` TEXT, `genre` TEXT, `status` INTEGER NOT NULL, `thumbnailUrl` TEXT, `coverPath` TEXT, `favorite` INTEGER NOT NULL, `initialized` INTEGER NOT NULL, `onlyFetchOnce` INTEGER NOT NULL, `dateAdded` INTEGER NOT NULL, `lastChapterFetchAt` INTEGER, `lastReadAt` INTEGER)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_sourceId_url` ON `manga` (`sourceId`, `url`)",
            "CREATE INDEX IF NOT EXISTS `index_manga_favorite` ON `manga` (`favorite`)",
            "CREATE INDEX IF NOT EXISTS `index_manga_title` ON `manga` (`title`)",
            "CREATE TABLE IF NOT EXISTS `manga_chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mangaId` INTEGER NOT NULL, `url` TEXT NOT NULL, `name` TEXT NOT NULL, `scanlator` TEXT, `chapterNumber` REAL NOT NULL, `dateUpload` INTEGER NOT NULL, `dateFetch` INTEGER NOT NULL, `sourceOrder` INTEGER NOT NULL, `read` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, `lastPageRead` INTEGER NOT NULL, `pageCount` INTEGER, FOREIGN KEY(`mangaId`) REFERENCES `manga`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_chapters_mangaId_url` ON `manga_chapters` (`mangaId`, `url`)",
            "CREATE INDEX IF NOT EXISTS `index_manga_chapters_read` ON `manga_chapters` (`read`)",
            "CREATE TABLE IF NOT EXISTS `manga_downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `chapterId` INTEGER NOT NULL, `mangaId` INTEGER NOT NULL, `status` TEXT NOT NULL, `path` TEXT, `pageCount` INTEGER, `downloadedPages` INTEGER NOT NULL, `sizeBytes` INTEGER NOT NULL, `queuedAt` INTEGER NOT NULL, `completedAt` INTEGER, `error` TEXT, FOREIGN KEY(`chapterId`) REFERENCES `manga_chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`mangaId`) REFERENCES `manga`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_manga_downloads_chapterId` ON `manga_downloads` (`chapterId`)",
            "CREATE INDEX IF NOT EXISTS `index_manga_downloads_mangaId` ON `manga_downloads` (`mangaId`)",
            "CREATE INDEX IF NOT EXISTS `index_manga_downloads_status` ON `manga_downloads` (`status`)",
            "CREATE TABLE IF NOT EXISTS `extension_repos` (`baseUrl` TEXT NOT NULL, `name` TEXT NOT NULL, `shortName` TEXT, `website` TEXT NOT NULL, `signingKeyFingerprint` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`baseUrl`))",
            "CREATE TABLE IF NOT EXISTS `trusted_extensions` (`pkgName` TEXT NOT NULL, `versionCode` INTEGER NOT NULL, `signature` TEXT NOT NULL, `trustedAt` INTEGER NOT NULL, PRIMARY KEY(`pkgName`, `versionCode`, `signature`))",
        )
    }
}
