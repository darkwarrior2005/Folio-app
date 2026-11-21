package app.folio.manga.download

import app.folio.core.model.Manga
import app.folio.core.model.MangaChapter
import app.folio.data.db.MangaDao
import app.folio.data.db.MangaDownloadEntity
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class QueuedDownload(
    val chapter: MangaChapter,
    val mangaTitle: String,
    val sourceId: Long,
    val progress: DownloadProgress? = null,
)

/**
 * Queues, cancels and deletes chapter downloads. The filesystem is the source of truth for
 * what's actually downloaded ([downloadedSource]); the database queue (`manga_downloads`) is
 * only what's waiting, so a killed process can resume it on next launch.
 */
class DownloadManager(
    private val provider: DownloadProvider,
    private val mangaDao: MangaDao,
) {

    private val _queue = MutableStateFlow<List<QueuedDownload>>(emptyList())
    val queue: StateFlow<List<QueuedDownload>> = _queue

    fun observeQueue(): Flow<List<MangaDownloadEntity>> = mangaDao.observeDownloadQueue()

    suspend fun enqueue(manga: Manga, chapters: List<MangaChapter>) {
        val now = System.currentTimeMillis()
        chapters.forEach { chapter ->
            if (mangaDao.download(chapter.id) == null) {
                mangaDao.enqueueDownload(MangaDownloadEntity(mangaId = manga.id, chapterId = chapter.id, queuedAt = now))
            }
        }
        _queue.update { current ->
            current + chapters.filterNot { c -> current.any { it.chapter.id == c.id } }
                .map { QueuedDownload(it, manga.title, manga.sourceId) }
        }
    }

    fun updateProgress(chapterId: Long, progress: DownloadProgress) {
        _queue.update { current -> current.map { if (it.chapter.id == chapterId) it.copy(progress = progress) else it } }
    }

    suspend fun dequeue(chapterIds: List<Long>) {
        mangaDao.dequeue(chapterIds)
        _queue.update { current -> current.filterNot { it.chapter.id in chapterIds } }
    }

    fun isDownloaded(sourceId: Long, mangaTitle: String, chapterName: String): Boolean =
        provider.isDownloaded(sourceId, mangaTitle, chapterName)

    /** Treats disk as authority: true only if the CBZ file actually exists and isn't empty. */
    fun downloadedSource(sourceId: Long, mangaTitle: String, chapterName: String): java.io.File? {
        val file = provider.chapterFile(sourceId, mangaTitle, chapterName)
        return file.takeIf { it.exists() && it.length() > 0 }
    }

    suspend fun deleteDownload(sourceId: Long, mangaTitle: String, chapter: MangaChapter) {
        provider.deleteChapter(sourceId, mangaTitle, chapter.name)
        dequeue(listOf(chapter.id))
    }

    suspend fun deleteManga(source: HttpSource, mangaTitle: String) {
        provider.deleteManga(source.id, mangaTitle)
    }

    /**
     * Fixes download badges for chapters whose CBZ was deleted from outside Folio (a file
     * manager, clearing storage) - the database queue doesn't know until this runs.
     */
    suspend fun healMissingFiles(sourceId: Long, mangaTitle: String, chapters: List<MangaChapter>): List<Long> {
        val missing = chapters.filter { !isDownloaded(sourceId, mangaTitle, it.name) }
        if (missing.isNotEmpty()) dequeue(missing.map { it.id })
        return missing.map { it.id }
    }
}
