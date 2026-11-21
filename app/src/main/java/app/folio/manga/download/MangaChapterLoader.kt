package app.folio.manga.download

import app.folio.core.model.MangaChapter
import app.folio.data.files.FileAccess
import app.folio.reader.comic.ComicInfo
import app.folio.reader.comic.ComicSource
import app.folio.reader.comic.ZipComicSource
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Opens a chapter either from its downloaded CBZ, or by streaming pages from the source. */
class MangaChapterLoader(private val provider: DownloadProvider, private val files: FileAccess) {

    suspend fun open(
        source: HttpSource,
        mangaTitle: String,
        chapter: MangaChapter,
    ): Result<ComicSource> = withContext(Dispatchers.IO) {
        val downloaded = provider.chapterFile(source.id, mangaTitle, chapter.name)
        if (downloaded.exists() && downloaded.length() > 0) {
            ZipComicSource.open(files, "file://${downloaded.path}")
        } else {
            val sChapter = SChapter.create().apply { url = chapter.url; name = chapter.name }
            val pages = source.getPageList(sChapter)
            Result.success(RemoteComicSource(source, pages))
        }
    }
}

/**
 * Streams pages straight from the source with no download. Only ever caches for the current
 * session (an in-memory map, cleared on close) and preloads [PRELOAD_AHEAD] pages, [PARALLEL] at
 * a time, so the reader rarely waits on a page that's already been requested.
 */
class RemoteComicSource(
    private val source: HttpSource,
    private val pages: List<Page>,
) : ComicSource {

    override val pageCount: Int = pages.size
    override val pageNames: List<String> = pages.indices.map { "%04d".format(it) }

    private val cache = java.util.concurrent.ConcurrentHashMap<Int, ByteArray>()
    private val inFlight = java.util.concurrent.ConcurrentHashMap<Int, Mutex>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun pageBytes(index: Int): ByteArray? {
        cache[index]?.let {
            preload(index)
            return it
        }
        val mutex = inFlight.computeIfAbsent(index) { Mutex() }
        return mutex.withLock {
            cache[index]?.let { return it }
            val bytes = fetchPage(index)
            if (bytes != null) cache[index] = bytes
            preload(index)
            bytes
        }
    }

    private fun preload(fromIndex: Int) {
        scope.launch {
            coroutineScope {
                ((fromIndex + 1)..(fromIndex + PRELOAD_AHEAD)).filter { it < pageCount && !cache.containsKey(it) }
                    .take(PARALLEL)
                    .map { async { fetchPage(it)?.let { bytes -> cache[it] = bytes } } }
            }
        }
    }

    private suspend fun fetchPage(index: Int): ByteArray? = try {
        val page = pages[index]
        if (page.imageUrl.isNullOrEmpty()) {
            val resolveResponse = source.client.newCall(source.imageUrlRequest(page)).execute()
            page.imageUrl = source.imageUrlParse(resolveResponse)
            resolveResponse.close()
        }
        val response = source.fetchImage(page)
        response.body.bytes().also { response.close() }
    } catch (e: Exception) {
        null
    }

    override suspend fun comicInfo(): ComicInfo? = null

    override fun close() {
        cache.clear()
        scope.cancel()
    }

    companion object {
        const val PRELOAD_AHEAD = 4
        const val PARALLEL = 3
    }
}
