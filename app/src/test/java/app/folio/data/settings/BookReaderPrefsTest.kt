package app.folio.data.settings

import app.folio.core.model.NormalizedOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookReaderPrefsTest {

    @Test
    fun prefsSavedByVersionOneStillDecode() {
        val v1 = """{"comicDirection":"RTL","comicDoublePage":null,"comicFitMode":null,""" +
            """"pdfViewMode":null,"pdfFitMode":null,"pdfRotation":null,"pdfCropMargins":null,"scrollMode":null}"""
        val prefs = BookReaderPrefs.decode(v1)
        assertEquals(ComicDirection.RTL, prefs.comicDirection)
        assertNull(prefs.zoomLocked)
    }

    @Test
    fun zoomStateRoundTrips() {
        val prefs = BookReaderPrefs(
            zoom = 2.5f,
            textSizePercent = 135,
            zoomLocked = true,
            lockButtonPortrait = NormalizedOffset(0f, 0.3f),
        )
        assertEquals(prefs, BookReaderPrefs.decode(BookReaderPrefs.encode(prefs)))
    }

    @Test
    fun effectiveTextSizePrefersTheBookOverride() {
        val settings = AppSettings(reflowable = ReflowableSettings(fontSizePercent = 100))
        assertEquals(100, BookReaderPrefs().effectiveTextSize(settings))
        assertEquals(140, BookReaderPrefs(textSizePercent = 140).effectiveTextSize(settings))
    }
}
