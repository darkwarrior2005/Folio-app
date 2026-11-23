package app.folio.manga.download

import android.content.Context
import app.folio.core.model.BookFormat
import app.folio.core.model.MangaDownloadStatus
import app.folio.data.db.FolioDatabase
import app.folio.data.db.MangaDownloadEntity
import app.folio.manga.MangaAccess
import app.folio.reader.api.BookSource
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * The download queue as the rest of the app sees it.
 *
 * Also the way the reader gets at a downloaded chapter: [downloadedSource] hands back a
 * [BookSource] pointing at the chapter on disk, which the existing comic reader opens exactly as
 * it opens any other CBZ. Offline reading is therefore the same reader, not a second one.
 */
class DownloadManager(
    private val context: Context,
    private val database: FolioDatabase,
    private val provider: DownloadProvider,
    private val access: MangaAccess,
    private val downloader: Downloader,
) {

    private val dao get() = database.manga()

    fun observeQueue(): Flow<List<MangaDownloadEntity>> = dao.observeQueue()

    fun observeQueueSize(): Flow<Int> = dao.observeQueueSize()

    fun observeDownloadsFor(mangaId: Long): Flow<List<MangaDownloadEntity>> = dao.observeDownloadsFor(mangaId)

    /** Queues chapters and starts the worker. Chapters already queued or downloaded are skipped. */
    suspend fun download(mangaId: Long, chapterIds: List<Long>) {
        access.require()

        val existing = dao.downloads(chapterIds).associateBy { it.chapterId }
        val now = System.currentTimeMillis()

        chapterIds.forEach { chapterId ->
            val current = existing[chapterId]
            when (current?.status) {
                MangaDownloadStatus.DOWNLOADED -> return@forEach
                MangaDownloadStatus.QUEUED, MangaDownloadStatus.DOWNLOADING -> return@forEach
                // A previous attempt failed; queue it again.
                MangaDownloadStatus.ERROR -> dao.updateDownload(
                    current.copy(status = MangaDownloadStatus.QUEUED, error = null, downloadedPages = 0),
                )
                null -> dao.enqueue(
                    MangaDownloadEntity(
                        chapterId = chapterId,
                        mangaId = mangaId,
                        status = MangaDownloadStatus.QUEUED,
                        queuedAt = now,
                    ),
                )
            }
        }

        DownloadWorker.start(context)
    }

    /** Takes chapters out of the queue, leaving anything already downloaded alone. */
    suspend fun cancel(chapterIds: List<Long>) {
        val queued = dao.downloads(chapterIds)
            .filter { it.status == MangaDownloadStatus.QUEUED || it.status == MangaDownloadStatus.DOWNLOADING }
        queued.forEach { downloader.cancel(it.chapterId) }
        dao.deleteDownloads(queued.map { it.chapterId })
    }

    suspend fun clearQueue() {
        dao.clearQueue()
    }

    /** Deletes the files and forgets the chapters were ever downloaded. */
    suspend fun delete(chapterIds: List<Long>) {
        dao.downloads(chapterIds).forEach { download ->
            download.path?.let { File(it).deleteRecursively() }
        }
        dao.deleteDownloads(chapterIds)
    }

    suspend fun deleteAllFor(mangaId: Long) {
        dao.downloadsForManga(mangaId).forEach { download ->
            download.path?.let { File(it).deleteRecursively() }
        }
        dao.deleteDownloadsFor(mangaId)
    }

    suspend fun isDownloaded(chapterId: Long): Boolean = downloadedSource(chapterId) != null

    /**
     * The downloaded chapter, ready for the comic reader, or null if it is not on disk.
     *
     * Disk is the authority: if the file has been deleted from under us the row is dropped, so a
     * chapter cannot appear downloaded when it is not.
     */
    suspend fun downloadedSource(chapterId: Long): BookSource? {
        val download = dao.download(chapterId) ?: return null
        if (download.status != MangaDownloadStatus.DOWNLOADED) return null

        val path = download.path?.let { File(it) }
        if (path == null || !path.exists()) {
            dao.deleteDownloads(listOf(chapterId))
            return null
        }

        return BookSource(
            bookId = chapterId,
            uri = path.toUriString(),
            format = if (path.isDirectory) BookFormat.IMAGE_FOLDER else BookFormat.CBZ,
            fileName = path.name,
        )
    }

    /**
     * Drops the DB row for any chapter marked downloaded whose file is actually gone.
     *
     * [downloadedSource] already does this check for one chapter when it is opened; nothing did
     * it for the whole library, so the "downloaded" badge on the library grid and the chapter
     * list could keep claiming a file was there long after it wasn't, failing only the moment the
     * user actually tried to read offline. Call this wherever those counts are about to be shown.
     */
    suspend fun healMissingFiles() {
        val missing = dao.completedDownloads().filter { download ->
            val path = download.path?.let { File(it) }
            path == null || !path.exists()
        }
        if (missing.isNotEmpty()) {
            dao.deleteDownloads(missing.map { it.chapterId })
        }
    }

    /** Total bytes the manga downloads take up. */
    suspend fun storageUsed(): Long = dao.completedDownloads()
        .mapNotNull { row -> row.path?.let { File(it) } }
        .filter { it.exists() }
        .sumOf { file ->
            if (file.isDirectory) {
                file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            } else {
                file.length()
            }
        }

    /** Removes scratch files left behind by an interrupted download. */
    fun clearTemporary() {
        provider.clearTmp()
    }

    private fun File.toUriString(): String = android.net.Uri.fromFile(this).toString()
}
