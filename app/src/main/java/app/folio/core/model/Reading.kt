package app.folio.core.model

import kotlinx.serialization.Serializable

enum class ReadingStatus { UNREAD, READING, FINISHED, ON_HOLD, ABANDONED }

enum class HighlightColor(val argb: Long) {
    YELLOW(0xFFF2C94C),
    BLUE(0xFF6FA8DC),
    GREEN(0xFF7BC47F),
    RED(0xFFE57373),
    PURPLE(0xFFB39DDB),
}

/**
 * The shared location model used by every reader, bookmark, highlight and note.
 *
 * - PDF / comics: [page] is the 0-based page index, [offset] the scroll fraction inside it.
 * - EPUB: [readiumLocator] holds the full Readium locator JSON; [href] mirrors its resource.
 * - Text: [page] is the block (paragraph) index, [offset] the fraction inside the block.
 */
@Serializable
data class BookLocation(
    val page: Int? = null,
    val offset: Double = 0.0,
    val href: String? = null,
    val totalProgression: Double = 0.0,
    val readiumLocator: String? = null,
    val zoom: Float? = null,
    val label: String? = null,
)

/** Character range inside a page / block, used for text highlights outside EPUB. */
@Serializable
data class TextRange(val start: Int, val end: Int)

data class TocEntry(
    val title: String,
    val location: BookLocation,
    val level: Int,
)

data class InBookSearchHit(
    val location: BookLocation,
    val snippet: String,
    val label: String,
    val range: TextRange? = null,
)
