package app.folio.reader.text.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConvertersTest {

    private fun sample(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/formats/$name")) { name }.use { it.readBytes() }

    private val dataImage = Regex("<img src=\"data:image/(png|jpeg);base64,")

    @Test
    fun mobi6TextMetadataPicturesAndPageBreaks() {
        val book = MobiConverter.convert(sample("sample.mobi"))
        assertEquals("The Long Road", book.title)
        assertEquals("Ada Writer", book.author)
        assertNotNull(book.cover)
        assertTrue(book.html.contains("Paragraph 0: café — naïve “quotes”"))
        assertTrue(book.html.contains("Paragraph 199:"))
        assertTrue(book.html.contains("<h2>Chapter Two</h2><p>The end.</p>"))
        assertTrue(book.html.contains("<hr>"))
        assertTrue(book.html.contains(Regex("<img src=\"data:image/png;base64,")))
        // Trailing entries are not text.
        assertFalse(book.html.contains('\u0001'))
    }

    @Test
    fun kf8ReadsTheFirstFlowOnlyAndResolvesEmbeds() {
        val book = MobiConverter.convert(sample("sample.azw3"))
        assertEquals("Das Buch", book.title)
        assertEquals("Kurt Autor", book.author)
        assertTrue(book.html.contains("Grüße aus der Ünterwelt."))
        assertTrue(book.html.contains("Kapitel Zwei"))
        assertFalse(book.html.contains("BODY-CSS-SHOULD-NOT-SHOW"))
        assertTrue(book.html.contains("src=\"data:image/jpeg;base64,"))
    }

    @Test
    fun drmIsRefusedClearly() {
        try {
            MobiConverter.convert(sample("drm.mobi"))
            fail("expected DRM")
        } catch (e: ConversionException) {
            assertEquals(ConversionException.Reason.DRM, e.reason)
        }
    }

    @Test
    fun notAMobiIsDamaged() {
        try {
            MobiConverter.convert(ByteArray(100))
            fail("expected an error")
        } catch (e: ConversionException) {
            assertEquals(ConversionException.Reason.DAMAGED, e.reason)
        }
    }

    @Test
    fun palmDocLiteralsPairsAndSpaces() {
        // "ab" + space-'c' (0xE3) + literal run of 2 high bytes + back-reference (distance 4, length 3).
        val input = byteArrayOf(0x61, 0x62, 0xE3.toByte(), 0x02, 0xC3.toByte(), 0xA9.toByte(), 0x80.toByte(), 0x20)
        val out = MobiConverter.palmDoc(input)
        assertEquals(listOf(0x61, 0x62, 0x20, 0x63, 0xC3, 0xA9, 0x20, 0x63, 0xC3), out.map { it.toInt() and 0xFF })
    }

    @Test
    fun trailingEntriesAreStripped() {
        // text "hi", multibyte overlap (1 byte + count byte), then one 4-byte entry whose size ends it.
        val record = byteArrayOf('h'.code.toByte(), 'i'.code.toByte(), 0x41, 0x01, 1, 2, 3, 0x84.toByte())
        assertEquals("hi", String(MobiConverter.stripTrailing(record, 0b11)))
        assertEquals(record.size, MobiConverter.stripTrailing(record, 0).size)
    }

    @Test
    fun fb2HonoursItsEncodingAndStructure() {
        for (name in listOf("sample.fb2", "sample.fb2.zip")) {
            val book = Fb2Converter.convert(sample(name))
            assertEquals("Война и мир", book.title)
            assertEquals("Лев Толстой", book.author)
            assertNotNull(book.cover)
            assertTrue(book.html.contains("<h1>Война и мир</h1>"))
            assertTrue(book.html.contains("<h2>Часть первая</h2>"))
            assertTrue(book.html.contains("<h3>Глава 1</h3>"))
            assertTrue(book.html.contains("<em>Генуя</em>"))
            assertTrue(book.html.contains("<blockquote>"))
            assertTrue(book.html.contains(dataImage))
            // The binary data itself is not shown as text.
            assertFalse(book.html.replace(Regex("data:image/[a-z]+;base64,[A-Za-z0-9+/=]+"), "").contains("iVBORw0KGgo"))
        }
    }

    @Test
    fun docxHeadingsEmphasisListsTablesAndPictures() {
        val book = DocxConverter.convert(sample("sample.docx"))
        assertEquals("Quarterly Report", book.title)
        assertEquals("Jo Smith", book.author)
        val html = book.html
        assertTrue(html.contains("<h1>Report &amp; Notes</h1>"))
        // A localised heading style ("Kop1", named "heading 1") is still a heading.
        assertTrue(html.contains("<h1>Inleiding</h1>"))
        assertTrue(html.contains("Plain then <strong>bold</strong> and <em>italic</em><br>new line"))
        assertTrue(html.contains("<ul><li>first item</li><li>second item</li></ul>"))
        assertTrue(html.contains("<p>a link text</p>"))
        assertTrue(html.contains("<td>A1</td><td>B1</td>"))
        assertTrue(html.contains(dataImage))
    }

    @Test
    fun odtHeadingsSpansListsAndPictures() {
        val book = OdtConverter.convert(sample("sample.odt"))
        assertEquals("Open Doc", book.title)
        assertEquals("Olga", book.author)
        val html = book.html
        assertTrue(html.contains("<h1>Open Heading</h1>"))
        assertTrue(html.contains("Some <strong>bold</strong> and <em>italic</em> words  spaced<br>next."))
        assertFalse(html.contains("footnote"))
        assertTrue(html.contains("<li>one</li><li>two</li>"))
        assertTrue(html.contains(dataImage))
    }

    @Test
    fun rtfTextFormattingAndInfo() {
        val book = RtfConverter.convert(sample("sample.rtf"))
        assertEquals("My RTF Title", book.title)
        assertEquals("Rita Author", book.author)
        val html = book.html
        assertTrue(html.contains("<p>Hello <strong>bold</strong> and <em>italic</em>.</p>"))
        assertTrue(html.contains("Café naïve “quoted” — dash<br>next line"))
        assertTrue(html.contains("<p>Picture gone.</p>"))
        assertTrue(html.contains("<p>link text</p>"))
        assertTrue(html.contains("Braces { and } and backslash \\."))
        assertFalse(html.contains("Times New Roman"))
        assertFalse(html.contains("Msftedit"))
        assertFalse(html.contains("HYPERLINK"))
    }

    @Test
    fun imageBudgetLeavesOutWhatDoesNotFit() {
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0, 0, 0, 0)
        val budget = ImageBudget(remaining = 10)
        assertNotNull(budget.dataUri(png))
        assertNull(budget.dataUri(png))
        assertNull(ImageBudget().dataUri(byteArrayOf(1, 2, 3)))
    }
}
