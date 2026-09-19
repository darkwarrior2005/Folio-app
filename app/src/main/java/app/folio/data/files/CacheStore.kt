package app.folio.data.files

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/**
 * Where generated and copied files live.
 *
 * filesDir (kept until the user deletes the book):
 *   covers/      extracted and user-chosen covers
 *   imported/    books copied in from share / "open with"
 * cacheDir (safe to clear any time):
 *   thumbs/      library thumbnails
 *   pages/       rendered PDF and comic pages
 *   tmp/         archives staged for random access
 */
class CacheStore(private val context: Context) {

    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    val importedDir = File(context.filesDir, "imported").apply { mkdirs() }
    val thumbsDir = File(context.cacheDir, "thumbs").apply { mkdirs() }
    val pagesDir = File(context.cacheDir, "pages").apply { mkdirs() }
    val tmpDir = File(context.cacheDir, "tmp").apply { mkdirs() }

    fun coverFile(bookId: Long, custom: Boolean): File =
        File(coversDir, if (custom) "book_${bookId}_custom.webp" else "book_$bookId.webp")

    fun writeCover(bookId: Long, bitmap: Bitmap, custom: Boolean): String? = try {
        val target = coverFile(bookId, custom)
        target.outputStream().use { out ->
            @Suppress("DEPRECATION")
            val format = if (android.os.Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, COVER_QUALITY, out)
        }
        target.absolutePath
    } catch (e: Exception) {
        null
    }

    fun deleteCovers(bookId: Long) {
        coverFile(bookId, custom = false).delete()
        coverFile(bookId, custom = true).delete()
    }

    fun trackCoverFile(trackId: Long): File = File(coversDir, "track_$trackId.jpg")

    fun writeTrackCover(trackId: Long, bitmap: Bitmap): String? = try {
        val target = trackCoverFile(trackId)
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        target.absolutePath
    } catch (e: Exception) {
        null
    }

    fun pageCacheFile(bookId: Long, page: Int, width: Int): File =
        File(pagesDir, "b${bookId}_p${page}_w$width.webp")

    fun stagedArchive(bookId: Long, extension: String): File =
        File(tmpDir, "book_$bookId.$extension")

    fun size(dir: File): Long =
        dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun sizes(): StorageUsage = StorageUsage(
        covers = size(coversDir),
        thumbnails = size(thumbsDir),
        renderedPages = size(pagesDir),
        temporary = size(tmpDir),
        importedFiles = size(importedDir),
    )

    fun clearThumbnails() = clearDir(thumbsDir)

    fun clearRenderedPages() = clearDir(pagesDir)

    fun clearTemporary() = clearDir(tmpDir)

    /** Clears everything regenerable. Books, covers and metadata are untouched. */
    fun clearAllCaches() {
        clearThumbnails()
        clearRenderedPages()
        clearTemporary()
    }

    private fun clearDir(dir: File) {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        dir.mkdirs()
    }

    companion object {
        private const val COVER_QUALITY = 88
    }
}

data class StorageUsage(
    val covers: Long,
    val thumbnails: Long,
    val renderedPages: Long,
    val temporary: Long,
    val importedFiles: Long,
) {
    val clearable: Long get() = thumbnails + renderedPages + temporary
}
