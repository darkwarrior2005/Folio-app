package app.folio.reader.api

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import app.folio.core.model.BookFormat
import app.folio.core.model.BookLocation
import app.folio.core.model.BookMetadata
import app.folio.core.model.InBookSearchHit
import app.folio.core.model.InkPoint
import app.folio.core.model.InkStroke
import app.folio.core.model.InkStyle
import app.folio.core.model.InkTool
import app.folio.core.model.InkToolStyle
import app.folio.core.model.ReaderFamily
import app.folio.core.model.TextRange
import app.folio.core.model.TocEntry
import app.folio.data.db.BookmarkEntity
import app.folio.data.db.HighlightEntity
import app.folio.data.settings.AppSettings
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** A book to open, independent of how it is stored. */
data class BookSource(
    val bookId: Long,
    val uri: String,
    val format: BookFormat,
    val fileName: String,
)

data class ExtractedMetadata(
    val metadata: BookMetadata = BookMetadata(),
    val pageCount: Int? = null,
    val cover: Bitmap? = null,
    val passwordProtected: Boolean = false,
    /** Set by comic metadata (ComicInfo.xml) when it declares right-to-left reading. */
    val rightToLeft: Boolean? = null,
)

/** One indexable piece of a book, with the location to jump back to. */
data class TextChunk(
    val location: BookLocation,
    val label: String,
    val text: String,
)

sealed interface BookOpenError {
    data object FileMissing : BookOpenError
    data object PermissionDenied : BookOpenError
    data object PasswordRequired : BookOpenError
    data object WrongPassword : BookOpenError
    data object Unsupported : BookOpenError
    data object Empty : BookOpenError
    data class Corrupted(val detail: String? = null) : BookOpenError
    data class Failed(val detail: String? = null) : BookOpenError
}

interface MetadataExtractor {
    suspend fun extract(source: BookSource): Result<ExtractedMetadata>
}

interface TextExtractor {
    /** Emits chunks as they are produced so indexing a large book stays incremental. */
    suspend fun extract(source: BookSource, onChunk: suspend (TextChunk) -> Unit)
}

/** What the shared reader chrome (top bar, TOC, search, bookmarks) can ask of any reader. */
@Stable
interface ReaderController {
    val supportsTextSelection: Boolean get() = false
    val supportsSearch: Boolean get() = false
    val supportsPageThumbnails: Boolean get() = false

    fun goTo(location: BookLocation)
    fun nextPage()
    fun previousPage()
    fun clearSelection() {}
    suspend fun search(query: String): List<InBookSearchHit> = emptyList()
    suspend fun thumbnail(page: Int, widthPx: Int): Bitmap? = null

    /** Returns magnified readers to fit; reflowable readers keep their text size. */
    fun resetZoom() {}
}

data class ReaderSelection(
    val text: String,
    val location: BookLocation,
    val positionLabel: String,
    val progress: Float,
    val page: Int? = null,
    val range: TextRange? = null,
    /** Where the selection is on screen, so the toolbar can avoid covering it. */
    val anchor: Rect? = null,
    val existingHighlightId: Long? = null,
)

/** Messages a reader may show about zooming. Never sent while the zoom lock is on. */
sealed interface ZoomNotice {
    data object ReflowsToTextSize : ZoomNotice
    data class TextSizeLimit(val limit: app.folio.core.model.ZoomLimit) : ZoomNotice
    data object OutOfMemory : ZoomNotice
}

/** Drawing mode as page readers see it. Only PDF and comic readers draw on pages. */
data class ScribbleState(
    val active: Boolean = false,
    val tool: InkTool = InkTool.PEN,
    val style: InkToolStyle = InkStyle.PEN_DEFAULT,
)

/**
 * Everything a format reader needs from the app, and everything it reports back. Implemented by
 * the reader screen's view model, so readers never touch the database themselves.
 */
@Stable
interface ReaderHost {
    val source: BookSource
    val initialLocation: BookLocation?
    val settings: StateFlow<AppSettings>
    val highlights: StateFlow<List<HighlightEntity>>
    val bookmarks: StateFlow<List<BookmarkEntity>>

    /** Jump requests coming from the table of contents, search results or bookmarks. */
    val jumps: SharedFlow<BookLocation>

    /** Password the user typed for a protected file; never stored. */
    val password: StateFlow<String?>

    /** Per-book reader overrides, such as a manga's reading direction. */
    val bookPrefs: StateFlow<app.folio.data.settings.BookReaderPrefs>

    fun updateBookPrefs(transform: (app.folio.data.settings.BookReaderPrefs) -> app.folio.data.settings.BookReaderPrefs)

    fun onProgress(location: BookLocation, progress: Float, page: Int?, pageCount: Int?, label: String)
    fun onToc(entries: List<TocEntry>)
    fun onError(error: BookOpenError)
    fun onCenterTap()
    fun onSelectionChanged(selection: ReaderSelection?)
    fun onControllerReady(controller: ReaderController?)
    fun requestPassword()

    fun onZoomNotice(notice: ZoomNotice)

    /** Short zoom label for the reader chrome, e.g. "250%"; null hides the chip. */
    fun onZoomLabel(label: String?)

    val scribble: StateFlow<ScribbleState>

    /** Saved page ink (PDF and comics), by page index. */
    val pageInk: StateFlow<Map<Int, List<InkStroke>>>

    /** A finished pen or highlighter stroke, in whole-page coordinates. */
    fun onInkStroke(page: Int, points: List<InkPoint>, heightOverWidth: Float)

    /** The eraser touched [point]; [radius] is in page widths. */
    fun onInkErase(page: Int, point: InkPoint, radius: Float, heightOverWidth: Float)

    fun onInkEraseEnd()
}

interface ReaderEngine {
    val family: ReaderFamily
    val metadataExtractor: MetadataExtractor
    val textExtractor: TextExtractor?

    fun handles(format: BookFormat): Boolean = format.family == family

    @Composable
    fun Content(host: ReaderHost)
}
