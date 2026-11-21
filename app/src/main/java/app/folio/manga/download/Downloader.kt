package app.folio.manga.download

import app.folio.core.model.MangaChapter
import app.folio.data.settings.SettingsRepository
import app.folio.reader.comic.ComicInfo
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class DownloadProgress {
    data class PageProgress(val completed: Int, val total: Int) : DownloadProgress()
    object Done : DownloadProgress()
    data class Failed(val reason: String) : DownloadProgress()
}

/**
 * Downloads one chapter at a time, its pages in parallel. Pages land in a scratch folder and are
 * only moved into the final CBZ once every one of them has succeeded, so a cancelled or failed
 * download never leaves a half-written chapter that looks complete.
 */
class Downloader(
    private val provider: DownloadProvider,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {

    private val activeJobs = ConcurrentHashMap<Long, Job>()

    fun isActive(chapterId: Long): Boolean = activeJobs[chapterId]?.isActive == true

    fun cancel(chapterId: Long) {
        activeJobs.remove(chapterId)?.cancel()
    }

    /** Suspends until the chapter is fully downloaded (or throws); [onProgress] reports pages done. */
    suspend fun downloadChapter(
        source: HttpSource,
        mangaTitle: String,
        chapter: MangaChapter,
        sChapter: SChapter,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val job = scope.launch(Dispatchers.IO) {
            try {
                doDownload(source, mangaTitle, chapter, sChapter, onProgress)
            } catch (e: Exception) {
                onProgress(DownloadProgress.Failed(e.message ?: "Download failed"))
            } finally {
                activeJobs.remove(chapter.id)
            }
        }
        activeJobs[chapter.id] = job
        job.join()
    }

    private suspend fun doDownload(
        source: HttpSource,
        mangaTitle: String,
        chapter: MangaChapter,
        sChapter: SChapter,
        onProgress: (DownloadProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val pages = source.getPageList(sChapter)
        val tempDir = provider.tempChapterDir(source.id, mangaTitle, chapter.name)
        val parallelism = settings.current().manga.parallelPageDownloads.coerceIn(1, 6)

        var completed = 0
        pages.withIndex().chunked(parallelism).forEach { batch ->
            batch.map { (index, page) ->
                async {
                    if (page.imageUrl.isNullOrEmpty()) {
                        val resolveResponse = source.client.newCall(source.imageUrlRequest(page)).execute()
                        page.imageUrl = source.imageUrlParse(resolveResponse)
                        resolveResponse.close()
                    }
                    val response = source.fetchImage(page)
                    val bytes = response.body.bytes()
                    response.close()
                    File(tempDir, "%04d.img".format(index)).writeBytes(bytes)
                }
            }.awaitAll()
            completed += batch.size
            onProgress(DownloadProgress.PageProgress(completed, pages.size))
        }

        val pageFiles = tempDir.listFiles { f -> f.name.endsWith(".img") }
            ?.sortedBy { it.name }
            ?: emptyList()
        if (pageFiles.size != pages.size) {
            throw IllegalStateException("Only ${pageFiles.size} of ${pages.size} pages downloaded")
        }

        val writeComicInfo = settings.current().manga.writeComicInfo
        val finalFile = provider.chapterFile(source.id, mangaTitle, chapter.name)
        ZipOutputStream(finalFile.outputStream().buffered()).use { zip ->
            pageFiles.forEachIndexed { index, file ->
                zip.putNextEntry(ZipEntry("%04d.jpg".format(index)))
                zip.write(file.readBytes())
                zip.closeEntry()
            }
            if (writeComicInfo) {
                zip.putNextEntry(ZipEntry("ComicInfo.xml"))
                zip.write(
                    ComicInfo(
                        title = chapter.name,
                        series = mangaTitle,
                        number = chapter.chapterNumber.toDouble().takeIf { it >= 0 },
                        pageCount = pageFiles.size,
                    ).toXml().toByteArray(),
                )
                zip.closeEntry()
            }
        }
        tempDir.deleteRecursively()
        onProgress(DownloadProgress.Done)
    }
}
