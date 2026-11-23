package app.folio.manga.download

import app.folio.core.model.MangaDownloadStatus
import app.folio.data.db.FolioDatabase
import app.folio.data.db.MangaChapterEntity
import app.folio.data.db.MangaDownloadEntity
import app.folio.data.db.MangaEntity
import app.folio.data.settings.SettingsRepository
import app.folio.manga.MangaAccess
import app.folio.manga.source.SourceManager
import app.folio.manga.toSChapter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Downloads queued chapters, one chapter at a time with its pages in parallel.
 *
 * Nothing is written where the reader can see it until a chapter is complete: pages go to a
 * scratch directory under the cache and are moved into place at the end. A download interrupted
 * by the process dying therefore leaves no half-read chapter behind, only scratch files, and the
 * queue row goes back to [MangaDownloadStatus.QUEUED] on the next run.
 *
 * Adapted from Mihon (Apache-2.0), which downloads from several sources at once; this keeps to
 * one chapter at a time so a source is never hit by more requests than its own rate limit allows.
 */
class Downloader(
    private val database: FolioDatabase,
    private val sources: SourceManager,
    private val provider: DownloadProvider,
    private val settings: SettingsRepository,
    private val access: MangaAccess,
) {

    private val dao get() = database.manga()

    /** The chapter currently being fetched, by chapter id, so [cancel] can interrupt it. */
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    /**
     * Works through the queue until it is empty. Returns false when it stopped early — manga
     * sources switched off, or WorkManager asked it to stop (lost network, hit its execution
     * budget) — so the caller retries instead of treating a still-queued batch as finished.
     */
    suspend fun runQueue(isStopped: () -> Boolean = { false }): Boolean {
        if (!access.enabled()) return false

        // Anything the last run left mid-flight is queued again.
        dao.requeueInterrupted()

        while (true) {
            if (isStopped()) return false
            val next = dao.queue().firstOrNull() ?: break
            if (!access.enabled()) return false
            coroutineScope {
                val job = launch { downloadChapter(next) }
                activeJobs[next.chapterId] = job
                try {
                    job.join()
                } finally {
                    activeJobs.remove(next.chapterId, job)
                }
            }
        }
        return true
    }

    /**
     * Interrupts a chapter's in-flight download, if one is running. Without this, cancelling a
     * download only deletes its queue row while the coroutine writing its file keeps going,
     * leaving a finished file on disk with nothing in the database pointing at it.
     */
    fun cancel(chapterId: Long) {
        activeJobs[chapterId]?.cancel()
    }

    private suspend fun downloadChapter(entry: MangaDownloadEntity) {
        val chapter = dao.chapter(entry.chapterId)
        val manga = dao.get(entry.mangaId)
        if (chapter == null || manga == null) {
            // The manga or chapter went away while it sat in the queue.
            dao.deleteDownloads(listOf(entry.chapterId))
            return
        }

        try {
            dao.updateDownload(entry.copy(status = MangaDownloadStatus.DOWNLOADING, error = null))
            val result = fetch(manga, chapter, entry)
            dao.markDownloaded(
                chapterId = entry.chapterId,
                path = result.file.absolutePath,
                pages = result.pageCount,
                sizeBytes = result.sizeBytes,
                now = System.currentTimeMillis(),
            )
        } catch (e: CancellationException) {
            // Put it back so a cancelled run resumes rather than losing the chapter.
            dao.updateDownload(entry.copy(status = MangaDownloadStatus.QUEUED))
            throw e
        } catch (e: Exception) {
            dao.updateDownload(
                entry.copy(
                    status = MangaDownloadStatus.ERROR,
                    error = e.message ?: e::class.simpleName ?: "Download failed",
                ),
            )
        }
    }

    private class Downloaded(val file: File, val pageCount: Int, val sizeBytes: Long)

    private suspend fun fetch(
        manga: MangaEntity,
        chapter: MangaChapterEntity,
        entry: MangaDownloadEntity,
    ): Downloaded {
        val source = sources.get(manga.sourceId)
            ?: error(sources.unavailableMessage(manga.sourceId))
        val httpSource = source as? HttpSource
            ?: error("${source.name} cannot download pages")

        val pages = httpSource.getPageList(chapter.toSChapter())
        require(pages.isNotEmpty()) { "The source returned no pages for this chapter" }

        val mangaSettings = settings.current().manga
        val tmpDir = provider.tmpChapterDir(chapter)
        tmpDir.listFiles()?.forEach { it.delete() }

        val completed = AtomicInteger(0)
        val slots = Semaphore(mangaSettings.parallelPageDownloads.coerceIn(1, MAX_PARALLEL_PAGES))

        coroutineScope {
            pages.mapIndexed { index, page ->
                async(Dispatchers.IO) {
                    slots.withPermit {
                        downloadPage(httpSource, page, tmpDir, index)
                        dao.updateDownload(
                            entry.copy(
                                status = MangaDownloadStatus.DOWNLOADING,
                                pageCount = pages.size,
                                downloadedPages = completed.incrementAndGet(),
                            ),
                        )
                    }
                }
            }.awaitAll()
        }

        val files = tmpDir.listFiles()?.filter { it.isFile && !it.name.endsWith(TMP_SUFFIX) }.orEmpty()
        check(files.size == pages.size) {
            "Only ${files.size} of ${pages.size} pages could be downloaded"
        }

        if (mangaSettings.writeComicInfo) {
            File(tmpDir, COMIC_INFO).writeText(comicInfoXml(manga, chapter, pages.size))
        }

        return withContext(Dispatchers.IO) {
            if (mangaSettings.saveChaptersAsCbz) {
                val target = provider.chapterArchive(source.name, manga, chapter)
                target.parentFile?.mkdirs()
                archive(tmpDir, target)
                tmpDir.deleteRecursively()
                Downloaded(target, pages.size, target.length())
            } else {
                val target = provider.chapterDir(source.name, manga, chapter)
                target.parentFile?.mkdirs()
                target.deleteRecursively()
                if (!tmpDir.renameTo(target)) {
                    // A rename across filesystems fails; copying is the fallback.
                    tmpDir.copyRecursively(target, overwrite = true)
                    tmpDir.deleteRecursively()
                }
                Downloaded(target, pages.size, target.walkTopDown().filter { it.isFile }.sumOf { it.length() })
            }
        }
    }

    private suspend fun downloadPage(source: HttpSource, page: Page, tmpDir: File, index: Int) {
        // Some sources only reveal the image URL on a second request.
        // Like Mihon: an empty link is as unresolved as a missing one.
        if (page.imageUrl.isNullOrEmpty()) {
            page.imageUrl = source.getImageUrl(page)
        }

        val response = source.getImage(page)
        val extension = imageExtension(response.header("Content-Type"), page.imageUrl)
        val name = "%03d.%s".format(index + 1, extension)

        val staging = File(tmpDir, "$name$TMP_SUFFIX")
        try {
            response.body.byteStream().use { input ->
                staging.outputStream().use { output -> input.copyTo(output) }
            }
            check(staging.length() > 0) { "Page ${index + 1} came back empty" }
            val target = File(tmpDir, name)
            target.delete()
            check(staging.renameTo(target)) { "Could not store page ${index + 1}" }
        } finally {
            response.close()
            staging.delete()
        }
    }

    /**
     * Stored without compression: the pages are already JPEG or WebP, so deflating them costs
     * time and saves nothing.
     */
    private fun archive(tmpDir: File, target: File) {
        val staging = File(target.parentFile, target.name + TMP_SUFFIX)
        ZipOutputStream(staging.outputStream().buffered()).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            tmpDir.listFiles().orEmpty()
                .filter { it.isFile && !it.name.endsWith(TMP_SUFFIX) }
                .sortedBy { it.name }
                .forEach { file ->
                    zip.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        target.delete()
        check(staging.renameTo(target)) { "Could not store the chapter archive" }
    }

    private fun imageExtension(contentType: String?, imageUrl: String?): String {
        val fromType = contentType?.substringAfter('/', "")?.substringBefore(';')?.trim()?.lowercase()
        if (!fromType.isNullOrBlank() && fromType in KNOWN_IMAGE_TYPES) {
            return if (fromType == "jpeg") "jpg" else fromType
        }
        val fromUrl = imageUrl?.substringBefore('?')?.substringAfterLast('.', "")?.lowercase()
        if (!fromUrl.isNullOrBlank() && fromUrl in KNOWN_IMAGE_EXTENSIONS) return fromUrl
        return "jpg"
    }

    /**
     * A minimal ComicInfo.xml, so the downloaded archive carries its own metadata and any other
     * reader — including Folio's own comic reader — can show the series and chapter.
     */
    private fun comicInfoXml(manga: MangaEntity, chapter: MangaChapterEntity, pageCount: Int): String {
        fun tag(name: String, value: String?): String {
            val text = value?.takeIf { it.isNotBlank() } ?: return ""
            return "  <$name>${text.escapeXml()}</$name>\n"
        }

        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<ComicInfo xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
            append(tag("Title", chapter.name))
            append(tag("Series", manga.title))
            append(tag("Number", chapter.chapterNumber.takeIf { it >= 0 }?.let { formatChapterNumber(it) }))
            append(tag("Summary", manga.description))
            append(tag("Writer", manga.author))
            append(tag("Penciller", manga.artist))
            append(tag("Genre", manga.genre))
            append(tag("Translator", chapter.scanlator))
            append("  <PageCount>$pageCount</PageCount>\n")
            append("  <Manga>Yes</Manga>\n")
            append("</ComicInfo>\n")
        }
    }

    private fun formatChapterNumber(number: Float): String =
        if (number % 1f == 0f) number.toInt().toString() else number.toString()

    private fun String.escapeXml(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private companion object {
        const val TMP_SUFFIX = ".tmp"
        const val COMIC_INFO = "ComicInfo.xml"
        const val MAX_PARALLEL_PAGES = 5
        val KNOWN_IMAGE_TYPES = setOf("jpeg", "jpg", "png", "webp", "gif", "avif", "heif", "jxl")
        val KNOWN_IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "heif", "jxl")
    }
}
