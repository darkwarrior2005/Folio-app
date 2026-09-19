package app.folio.data.settings

import app.folio.core.model.NormalizedOffset
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Per-book reader overrides. A manga keeps its right-to-left direction, a scanned PDF keeps its
 * rotation, without changing how every other book opens. Null means "use the global setting".
 */
@Serializable
data class BookReaderPrefs(
    val comicDirection: ComicDirection? = null,
    val comicDoublePage: Boolean? = null,
    val comicFitMode: ComicFitMode? = null,
    val pdfViewMode: PdfViewMode? = null,
    val pdfFitMode: PdfFitMode? = null,
    val pdfRotation: Int? = null,
    val pdfCropMargins: Boolean? = null,
    val scrollMode: ReaderScrollMode? = null,
    /** Magnification for PDF, comic and fixed-layout EPUB (1 = fit). */
    val zoom: Float? = null,
    /** Text size for reflowable books; null follows Settings → Reader. */
    val textSizePercent: Int? = null,
    val zoomLocked: Boolean? = null,
    val lockButtonPortrait: NormalizedOffset? = null,
    val lockButtonLandscape: NormalizedOffset? = null,
    val reflowZoomNoticeShown: Boolean? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun decode(raw: String?): BookReaderPrefs =
            if (raw.isNullOrBlank()) {
                BookReaderPrefs()
            } else {
                try {
                    json.decodeFromString<BookReaderPrefs>(raw)
                } catch (e: Exception) {
                    BookReaderPrefs()
                }
            }

        fun encode(prefs: BookReaderPrefs): String = json.encodeToString(prefs)
    }
}

fun BookReaderPrefs.effectiveTextSize(settings: AppSettings): Int =
    textSizePercent ?: settings.reflowable.fontSizePercent
