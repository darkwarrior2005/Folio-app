package app.folio.manga.reader

import app.folio.data.db.FolioDatabase
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.manga.MangaAccess
import app.folio.manga.download.DownloadManager
import app.folio.manga.source.SourceManager
import app.folio.manga.toSChapter
import app.folio.reader.comic.ComicSource
import app.folio.reader.comic.ComicSources
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opens a manga chapter for reading, downloaded or not.
 *
 * Both cases come back as a [ComicSource], which is what Folio's comic reader already consumes,
 * so a chapter streamed from a source and a chapter read offline go through the same reader,
 * the same gestures and the same page cache.
 *
 * A downloaded chapter is always preferred: it is faster, it works with no connection, and it
 * does not hit the source again.
 */
class MangaChapterLoader(
    private val database: FolioDatabase,
    private val downloads: DownloadManager,
    private val sources: SourceManager,
    private val files: FileAccess,
    private val cache: CacheStore,
    private val access: MangaAccess,
) {

    suspend fun open(chapterId: Long): Result<ComicSource> {
        downloads.downloadedSource(chapterId)?.let { source ->
            return ComicSources.open(files, cache, source)
        }

        return try {
            Result.success(openFromSource(chapterId))
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: LinkageError) {
            // An extension calling an API Folio lacks: report it like any other failure.
            Result.failure(e)
        }
    }

    /** Whether reading this chapter right now would need the network. */
    suspend fun isAvailableOffline(chapterId: Long): Boolean = downloads.isDownloaded(chapterId)

    private suspend fun openFromSource(chapterId: Long): ComicSource {
        access.require()

        val dao = database.manga()
        val chapter = dao.chapter(chapterId) ?: error("Chapter not found")
        val manga = dao.get(chapter.mangaId) ?: error("Manga not found")

        val source = sources.get(manga.sourceId) as? HttpSource
            ?: error(sources.unavailableMessage(manga.sourceId))

        val pages = withContext(Dispatchers.IO) { source.getPageList(chapter.toSChapter()) }
        require(pages.isNotEmpty()) { "The source returned no pages for this chapter" }

        return RemoteComicSource(source, pages, File(cache.pagesDir, "manga_$chapterId").apply { mkdirs() })
    }
}

/**
 * A chapter read straight from its source.
 *
 * Like Mihon, it fetches ahead: opening a page also starts the next few, a handful at a time, so
 * turning the page usually finds it already there. Fetched pages are kept as files under the page
 * cache so flicking back does not fetch them again. The cache directory belongs to this chapter
 * alone and is cleared when the reader closes, which keeps a browsing session from quietly filling
 * up storage — downloading is the way to keep a chapter.
 */
private class RemoteComicSource(
    private val source: HttpSource,
    private val pages: List<eu.kanade.tachiyomi.source.model.Page>,
    private val cacheDir: File,
) : ComicSource {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Sites rate limit, so only a few pages are fetched at once. */
    private val permits = Semaphore(PARALLEL_PAGES)

    private val inFlight = mutableMapOf<Int, Deferred<ByteArray>>()
    private val lock = Mutex()

    override val pageCount: Int = pages.size

    override val pageNames: List<String> = pages.indices.map { "%03d".format(it + 1) }

    override suspend fun pageBytes(index: Int): ByteArray? {
        if (index !in pages.indices) return null
        val wanted = fetch(index)
        // The pages after this one, so they are ready by the time they are turned to.
        (index + 1..minOf(index + PRELOAD_AHEAD, pages.lastIndex)).forEach { fetch(it) }
        return wanted.await()
    }

    /** The fetch of page [index]: its cached file, one already running, or a new one. */
    private suspend fun fetch(index: Int): Deferred<ByteArray> = lock.withLock {
        // A failed fetch counts as cancelled, so asking again (Try again) starts a fresh one.
        inFlight[index]?.takeUnless { it.isCancelled }
            ?: scope.async { permits.withPermit { download(index) } }.also { inFlight[index] = it }
    }

    private suspend fun download(index: Int): ByteArray {
        val cached = File(cacheDir, "$index")
        if (cached.exists() && cached.length() > 0) return cached.readBytes()

        val page = pages[index]
        // An empty link is as unresolved as a missing one, as in Mihon.
        if (page.imageUrl.isNullOrEmpty()) {
            page.imageUrl = source.getImageUrl(page)
        }

        val bytes = source.getImage(page).use { it.body.bytes() }

        val staging = File(cacheDir, "$index.tmp")
        staging.writeBytes(bytes)
        staging.renameTo(cached)
        return bytes
    }

    override fun close() {
        scope.cancel()
        cacheDir.deleteRecursively()
    }

    private companion object {
        const val PRELOAD_AHEAD = 4
        const val PARALLEL_PAGES = 3
    }
}
