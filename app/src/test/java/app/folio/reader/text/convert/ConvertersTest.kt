package app.folio.reader.text.convert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ConvertersTest {

    @Test
    fun rtfConvertsBasicFormatting() {
        val rtf = """{\rtf1\ansi\ansicpg1252\deff0{\fonttbl{\f0 Times;}}{\info{\title My Title}}
            |\pard Hello \b bold\b0  and \i italic\i0 .\par
            |Second paragraph.\par}
        """.trimMargin()
        val book = RtfConverter.convert(rtf.toByteArray(Charsets.ISO_8859_1))
        assertEquals("My Title", book.title)
        assertTrue(book.html.contains("<b>bold</b>"))
        assertTrue(book.html.contains("<i>italic</i>"))
        assertTrue(book.html.contains("Second paragraph"))
    }

    @Test
    fun rtfRejectsNonRtfInput() {
        assertThrows(ConversionException::class.java) {
            RtfConverter.convert("not rtf at all".toByteArray())
        }
    }

    @Test
    fun fb2ConvertsTitleAndParagraphs() {
        val fb2 = """
            <?xml version="1.0" encoding="utf-8"?>
            <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
              <description>
                <title-info>
                  <book-title>Sample Book</book-title>
                  <author><first-name>Ada</first-name><last-name>Lovelace</last-name></author>
                </title-info>
              </description>
              <body>
                <section>
                  <title><p>Chapter One</p></title>
                  <p>First paragraph.</p>
                  <p>Second paragraph.</p>
                </section>
              </body>
            </FictionBook>
        """.trimIndent()
        val book = Fb2Converter.convert(fb2.toByteArray(Charsets.UTF_8), isZipped = false)
        assertEquals("Sample Book", book.title)
        assertEquals("Ada Lovelace", book.author)
        assertTrue(book.html.contains("First paragraph"))
        assertTrue(book.html.contains("Second paragraph"))
    }

    @Test
    fun palmDocDecompressesOverlappingBackReferencesAndSpacePairs() {
        // "abc", then a back-reference 3 bytes back for 6 bytes (overlaps its own output),
        // then 0xE1 = a space followed by 'a'.
        val compressed = byteArrayOf(0x61, 0x62, 0x63, 0x80.toByte(), 0x1B, 0xE1.toByte())
        assertEquals("abcabcabc a", String(MobiConverter.decompressPalmDoc(compressed), Charsets.ISO_8859_1))
    }

    @Test
    fun mobiRejectsNonMobiInput() {
        assertThrows(ConversionException::class.java) {
            MobiConverter.convert(ByteArray(100))
        }
    }

    @Test
    fun mobiDecodesAnUncompressedPalmDocRecord() {
        // A minimal PDB: record 0 is the bare PalmDOC header (no MOBI header), record 1 is one
        // uncompressed (compression type 1) text record.
        val text = "Hello uncompressed MOBI world."
        val textBytes = text.toByteArray(Charsets.ISO_8859_1)

        val header = ByteArray(78)
        System.arraycopy("BOOKMOBI".toByteArray(Charsets.ISO_8859_1), 0, header, 60, 8)
        ByteBuffer.wrap(header, 76, 2).order(ByteOrder.BIG_ENDIAN).putShort(2) // numRecords = 2

        val record0Offset = 78 + 2 * 8 // after both record-info entries
        val record0Size = 16
        val record1Offset = record0Offset + record0Size

        val recordInfo = ByteArray(16)
        ByteBuffer.wrap(recordInfo).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(0, record0Offset)
            putInt(8, record1Offset)
        }

        val record0 = ByteArray(record0Size).also {
            ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN).apply {
                putShort(0, 1) // compression = none
                putShort(8, 1) // record count = 1
                putShort(12, 0) // encryption type = none
            }
        }

        val bytes = header + recordInfo + record0 + textBytes
        val book = MobiConverter.convert(bytes)
        assertTrue(book.html.contains("Hello uncompressed MOBI world."))
    }
}
