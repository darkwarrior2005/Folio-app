package app.folio.manga.download

import android.content.Context
import java.io.File

/**
 * Downloaded chapters live under `manga/<source>/<title>/<chapter>.cbz` in app-private storage -
 * no storage permission needed, and the disk itself is what [DownloadManager] treats as the
 * source of truth for what's downloaded.
 */
class DownloadProvider(context: Context) {

    private val root = File(context.filesDir, "manga").apply { mkdirs() }

    fun sourceDir(sourceId: Long): File = File(root, sourceId.toString())

    fun mangaDir(sourceId: Long, mangaTitle: String): File =
        File(sourceDir(sourceId), sanitize(mangaTitle)).apply { mkdirs() }

    fun chapterFile(sourceId: Long, mangaTitle: String, chapterName: String): File =
        File(mangaDir(sourceId, mangaTitle), "${sanitize(chapterName)}.cbz")

    /** A chapter's own scratch folder while its pages are still being fetched. */
    fun tempChapterDir(sourceId: Long, mangaTitle: String, chapterName: String): File =
        File(mangaDir(sourceId, mangaTitle), "${sanitize(chapterName)}_tmp").apply { mkdirs() }

    fun isDownloaded(sourceId: Long, mangaTitle: String, chapterName: String): Boolean =
        chapterFile(sourceId, mangaTitle, chapterName).let { it.exists() && it.length() > 0 }

    fun deleteChapter(sourceId: Long, mangaTitle: String, chapterName: String) {
        chapterFile(sourceId, mangaTitle, chapterName).delete()
        tempChapterDir(sourceId, mangaTitle, chapterName).deleteRecursively()
    }

    fun deleteManga(sourceId: Long, mangaTitle: String) {
        mangaDir(sourceId, mangaTitle).deleteRecursively()
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[/\\:*?"<>|]"""), "_").trim().ifBlank { "untitled" }
}
