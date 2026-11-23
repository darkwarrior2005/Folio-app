package app.folio.manga.download

import android.content.Context
import app.folio.data.db.MangaChapterEntity
import app.folio.data.db.MangaEntity
import java.io.File

/**
 * Where a downloaded chapter lives.
 *
 * The layout is readable on purpose, so a user who copies the folder out still sees what is what:
 *
 *   manga/<source>/<manga title>/<chapter name>.cbz
 *
 * Unlike Mihon the root is Folio's own storage rather than a folder the user picks, so no storage
 * permission is involved and uninstalling takes the downloads with it.
 *
 * Adapted from Mihon (Apache-2.0).
 */
class DownloadProvider(context: Context) {

    val root: File = File(context.filesDir, "manga").apply { mkdirs() }

    /** Half-written downloads live here and are moved into place only once complete. */
    private val tmpRoot: File = File(context.cacheDir, "manga_tmp").apply { mkdirs() }

    fun sourceDir(sourceName: String): File = File(root, sanitize(sourceName))

    fun mangaDir(sourceName: String, manga: MangaEntity): File =
        File(sourceDir(sourceName), sanitize(manga.title))

    /** The finished archive for a chapter. */
    fun chapterArchive(sourceName: String, manga: MangaEntity, chapter: MangaChapterEntity): File =
        File(mangaDir(sourceName, manga), "${chapterDirName(chapter)}.cbz")

    /** The finished folder for a chapter, when archiving is turned off. */
    fun chapterDir(sourceName: String, manga: MangaEntity, chapter: MangaChapterEntity): File =
        File(mangaDir(sourceName, manga), chapterDirName(chapter))

    /** Scratch space for one chapter's pages, keyed by chapter so two never collide. */
    fun tmpChapterDir(chapter: MangaChapterEntity): File =
        File(tmpRoot, chapter.id.toString()).apply { mkdirs() }

    /**
     * The scanlator is part of the name because two groups' releases of the same chapter are
     * different files that would otherwise overwrite each other.
     */
    fun chapterDirName(chapter: MangaChapterEntity): String {
        val scanlator = chapter.scanlator?.takeIf { it.isNotBlank() }?.let { "${sanitize(it)}_" }.orEmpty()
        return sanitize("$scanlator${chapter.name}")
    }

    /** Removes everything downloaded for a manga. */
    fun deleteManga(sourceName: String, manga: MangaEntity): Boolean =
        mangaDir(sourceName, manga).deleteRecursively()

    fun clearTmp() {
        tmpRoot.listFiles()?.forEach { it.deleteRecursively() }
    }

    /**
     * Makes a name safe for any filesystem Android might mount, including FAT on an SD card, and
     * short enough to stay inside the 255-byte limit once an extension is added.
     */
    private fun sanitize(name: String): String {
        val cleaned = name
            .replace(RESERVED_CHARS, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
        val safe = cleaned.ifBlank { "unnamed" }
        return if (safe.length <= MAX_NAME_LENGTH) safe else safe.take(MAX_NAME_LENGTH).trimEnd()
    }

    private companion object {
        val RESERVED_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1f]")
        const val MAX_NAME_LENGTH = 180
    }
}
