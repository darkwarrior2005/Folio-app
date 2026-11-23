package app.folio.ui.screens.manga

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.manga.source.needsWebView
import app.folio.manga.source.sourceErrorMessage
import app.folio.reader.comic.ComicSource
import app.folio.reader.comic.ComicSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MangaReaderState(
    val chapterName: String = "",
    val mangaTitle: String = "",
    val pageCount: Int = 0,
    val initialPage: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    /** The error came from a site check the user can get past by opening the site. */
    val needsWebView: Boolean = false,
    val offline: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

/**
 * Holds the open chapter. Pages are decoded on demand and the open [ComicSource] is closed when
 * the reader goes away, which is also what clears a streamed chapter's page cache.
 */
class MangaReaderViewModel(
    private val container: AppContainer,
    private val chapterId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(MangaReaderState())
    val state: StateFlow<MangaReaderState> = _state.asStateFlow()

    private var comic: ComicSource? = null
    private var mangaId: Long = 0

    /** The source the open chapter streams from, for the "Open the site" action. */
    var sourceId: Long = 0
        private set
    private var currentChapterId: Long = chapterId

    /** Chapter ids in reading order, earliest first. */
    private var order: List<Long> = emptyList()

    /** The in-flight progress save for a chapter, if any, so [onLeavingChapter] can wait for it. */
    private val progressJobs = mutableMapOf<Long, Job>()

    init {
        open(chapterId)
    }

    fun open(id: Long) {
        // Leaving a chapter is the moment to tidy it up and to fetch what comes next.
        onLeavingChapter(currentChapterId)
        currentChapterId = id
        _state.value = MangaReaderState(loading = true)
        viewModelScope.launch {
            comic?.let { existing -> withContext(Dispatchers.IO) { existing.close() } }
            comic = null

            try {
                val chapter = container.manga.chapter(id) ?: error("Chapter not found")
                mangaId = chapter.mangaId
                val manga = container.manga.get(chapter.mangaId)
                sourceId = manga?.sourceId ?: 0

                // Source order counts down from the newest, so reading order is the reverse.
                order = container.manga.chapters(chapter.mangaId)
                    .sortedByDescending { it.sourceOrder }
                    .map { it.id }
                val position = order.indexOf(id)

                val offline = container.downloads.isDownloaded(id)
                val opened = container.mangaChapters.open(id).getOrThrow()
                comic = opened

                _state.value = MangaReaderState(
                    chapterName = chapter.name,
                    mangaTitle = manga?.title.orEmpty(),
                    pageCount = opened.pageCount,
                    initialPage = chapter.lastPageRead.coerceIn(0, (opened.pageCount - 1).coerceAtLeast(0)),
                    loading = false,
                    offline = offline,
                    hasPrevious = position > 0,
                    hasNext = position in 0 until order.lastIndex,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed(e)
            } catch (e: LinkageError) {
                // An extension built against an API Folio lacks; say so instead of crashing.
                failed(e)
            }
        }
    }

    /** Opens the current chapter again, after an error. */
    fun retry() = open(currentChapterId)

    private fun failed(e: Throwable) {
        _state.value = MangaReaderState(
            loading = false,
            error = e.sourceErrorMessage(),
            needsWebView = e.needsWebView(),
        )
    }

    /**
     * Fetches and decodes one page. Throws with a message the page can show when the page cannot
     * be fetched or its image cannot be read, so a failure is never an endless spinner.
     */
    suspend fun page(index: Int, targetWidthPx: Int): Bitmap {
        val source = comic ?: error("The chapter is not open")
        return withContext(Dispatchers.IO) {
            val bytes = try {
                source.pageBytes(index)
            } catch (e: LinkageError) {
                throw PageLoadException(e.sourceErrorMessage(), e)
            } ?: error("The source did not return this page")
            check(bytes.isNotEmpty()) { "The source returned an empty page" }
            ComicSources.decode(bytes, targetWidthPx)
                ?: error("This page's image could not be read (${bytes.size / 1024} KB, ${imageKind(bytes)})")
        }
    }

    fun onPageChanged(index: Int) {
        val chapterId = currentChapterId
        progressJobs[chapterId] = viewModelScope.launch {
            container.manga.saveProgress(chapterId, index, _state.value.pageCount)
        }
    }

    /**
     * Runs once a chapter is left behind, on the application scope so it still finishes when the
     * reader is being torn down.
     *
     * A chapter is only deleted when it was actually finished, and only when the user asked for
     * that; reading ahead queues the chapters after it.
     */
    private fun onLeavingChapter(chapterId: Long) {
        val manga = mangaId
        // mangaId is only set once a chapter has been loaded, so this does nothing on the first
        // open -- otherwise reopening an already-read chapter would delete it as it opened.
        if (chapterId == 0L || manga == 0L) return
        // onPageChanged's write runs on a different scope (viewModelScope vs. appScope); without
        // waiting for it, finishing a chapter and immediately tapping Next can get here before
        // that write lands, so the read-state check below sees a stale "not read" and bails.
        val pendingSave = progressJobs.remove(chapterId)
        container.appScope.launch {
            pendingSave?.join()
            val settings = container.settings.current().manga
            val chapter = container.manga.chapter(chapterId) ?: return@launch
            if (!chapter.read) return@launch

            if (settings.deleteChapterAfterRead) {
                container.downloads.delete(listOf(chapterId))
            }

            val ahead = settings.downloadAheadChapters
            if (ahead > 0) {
                val position = order.indexOf(chapterId)
                if (position >= 0) {
                    val next = order.drop(position + 1).take(ahead)
                        .filter { id -> container.manga.chapter(id)?.read == false }
                    if (next.isNotEmpty()) {
                        runCatching { container.downloads.download(manga, next) }
                    }
                }
            }
        }
    }

    fun previousChapterId(): Long? {
        val position = order.indexOf(currentChapterId)
        return if (position > 0) order[position - 1] else null
    }

    fun nextChapterId(): Long? {
        val position = order.indexOf(currentChapterId)
        return if (position in 0 until order.lastIndex) order[position + 1] else null
    }

    override fun onCleared() {
        onLeavingChapter(currentChapterId)
        val open = comic
        comic = null
        // Closing deletes a streamed chapter's cached pages.
        open?.let { source -> container.appScope.launch { withContext(Dispatchers.IO) { source.close() } } }
    }
}

/** A page that failed for a reason worth showing, such as an extension missing an API. */
class PageLoadException(message: String, cause: Throwable) : Exception(message, cause)

/** A short guess at what the bytes are, so an unreadable page says more than "failed". */
internal fun imageKind(bytes: ByteArray): String {
    fun startsWith(vararg prefix: Int) = bytes.size >= prefix.size && prefix.indices.all { bytes[it].toInt() and 0xFF == prefix[it] }
    fun ascii(at: Int, text: String) = bytes.size >= at + text.length && text.indices.all { bytes[at + it].toInt().toChar() == text[it] }
    return when {
        startsWith(0xFF, 0xD8, 0xFF) -> "JPEG"
        startsWith(0x89, 0x50, 0x4E, 0x47) -> "PNG"
        ascii(0, "GIF8") -> "GIF"
        ascii(0, "RIFF") && ascii(8, "WEBP") -> "WebP"
        ascii(4, "ftypavif") || ascii(4, "ftypavis") -> "AVIF"
        ascii(4, "ftypheic") || ascii(4, "ftypmif1") -> "HEIF"
        startsWith(0xFF, 0x0A) || ascii(4, "JXL ") -> "JPEG XL"
        bytes.take(64).toByteArray().decodeToString().trimStart().startsWith("<") -> "a web page, not an image"
        else -> "unknown format"
    }
}
