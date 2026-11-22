package app.folio.reader.text.convert

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

/**
 * A big-endian view of bytes[offset, offset+length) whose absolute get(i) indexes from offset.
 * (ByteBuffer.wrap(array, offset, length) alone would still index from the array's start.)
 */
private fun slice(bytes: ByteArray, offset: Int, length: Int): ByteBuffer =
    ByteBuffer.wrap(bytes, offset, length).slice().order(ByteOrder.BIG_ENDIAN)

/**
 * MOBI / PRC (PalmDOC-based) and AZW3 (KF8). Offsets follow the MobileRead wiki and foliate-js.
 *
 * Simplified from the full spec, by design given the time this port had: a combo MOBI+KF8 file's
 * separate KF8 flow via FDST (splitting flow 0 - the real text - from later flows that hold CSS/
 * SVG) is NOT implemented here; this always decodes the PalmDOC text records of the *first*
 * record 0 header it finds and treats that whole decompressed blob as the book. For a pure MOBI7
 * file that's exactly correct. For a combo/KF8-only file it can include some non-text markup
 * mixed into flow 0 rather than cleanly isolating it - acceptable for a first cut, flagged here
 * rather than silently claimed as complete.
 */
object MobiConverter {

    fun convert(bytes: ByteArray): ConvertedBook {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        if (bytes.size < 78) throw ConversionException(ConversionReason.DAMAGED, "File too small")

        val typeCreator = String(bytes, 60, 8)
        if (typeCreator != "BOOKMOBI" && typeCreator != "TEXtREAd") {
            throw ConversionException(ConversionReason.UNSUPPORTED, "Not a PalmDOC/MOBI file")
        }

        val numRecords = buffer.getShort(76).toInt() and 0xFFFF
        val recordOffsets = IntArray(numRecords) { i -> buffer.getInt(78 + i * 8) }

        fun recordRange(index: Int): IntRange {
            val start = recordOffsets[index]
            val end = if (index + 1 < numRecords) recordOffsets[index + 1] else bytes.size
            return start until end
        }

        // Record 0: PalmDOC header (16 bytes) then, for MOBI, the MOBI header + EXTH.
        val record0 = recordRange(0)
        val record0Buf = slice(bytes, record0.first, record0.last + 1 - record0.first)
        val compression = record0Buf.getShort(0).toInt()
        // PalmDOC header: compression(0), unused(2), text length(4), record count(8),
        // record size(10), encryption type(12).
        val recordCount = record0Buf.getShort(8).toInt() and 0xFFFF
        if (record0Buf.getShort(12).toInt() != 0) {
            throw ConversionException(ConversionReason.DRM, "This book is DRM protected")
        }

        var encoding: Charset = WINDOWS_1252
        var title: String? = null
        var author: String? = null
        var coverIndex = -1
        var resourceStart = Int.MAX_VALUE

        // MOBI header fields, at offsets counted from the start of record 0 (the MobileRead wiki
        // and foliate-js both count them this way): "MOBI" at 16, header length at 20, text
        // encoding at 28, full name offset/length at 84/88, first resource record at 108, EXTH
        // flags at 128, trailing-entry flags at 242. EXTH follows right after the MOBI header.
        val record0Length = record0.last + 1 - record0.first
        fun int0(offset: Int): Int? = if (offset + 4 <= record0Length) record0Buf.getInt(offset) else null
        val hasMobiHeader = record0Length >= 24 && String(bytes, record0.first + 16, 4) == "MOBI"
        var trailingFlags = 0
        if (hasMobiHeader) {
            val headerLength = int0(20) ?: 0
            encoding = if (int0(28) == 65001) Charsets.UTF_8 else WINDOWS_1252
            resourceStart = int0(108) ?: Int.MAX_VALUE
            val exthFlags = int0(128) ?: 0
            if (headerLength >= 0xE4 && 242 + 2 <= record0Length) {
                trailingFlags = record0Buf.getShort(242).toInt() and 0xFFFF
            }
            if ((exthFlags and 0x40) != 0) {
                val exthOffset = record0.first + 16 + headerLength
                if (exthOffset + 12 <= bytes.size && String(bytes, exthOffset, 4) == "EXTH") {
                    val exthBuf = slice(bytes, exthOffset, bytes.size - exthOffset)
                    val recCount = exthBuf.getInt(8)
                    var pos = 12
                    for (r in 0 until recCount) {
                        if (exthOffset + pos + 8 > bytes.size) break
                        val recType = exthBuf.getInt(pos)
                        val recLen = exthBuf.getInt(pos + 4)
                        if (recLen < 8) break
                        val dataStart = exthOffset + pos + 8
                        val dataLen = recLen - 8
                        if (dataStart + dataLen <= bytes.size) {
                            when (recType) {
                                100 -> author = String(bytes, dataStart, dataLen, Charsets.UTF_8)
                                503 -> title = String(bytes, dataStart, dataLen, Charsets.UTF_8)
                                201 -> if (dataLen >= 4) coverIndex = slice(bytes, dataStart, dataLen).getInt(0)
                            }
                        }
                        pos += recLen
                    }
                }
            }
            if (title == null) {
                val fullNameOffset = int0(84) ?: 0
                val fullNameLength = int0(88) ?: 0
                if (fullNameOffset > 0 && fullNameLength > 0 && fullNameOffset + fullNameLength <= record0Length) {
                    title = String(bytes, record0.first + fullNameOffset, fullNameLength, encoding)
                }
            }
        }

        val textBuilder = StringBuilder()
        for (i in 1..recordCount) {
            if (i >= numRecords) break
            val range = recordRange(i)
            var recordBytes = bytes.copyOfRange(range.first, range.last + 1)
            recordBytes = stripTrailingEntries(recordBytes, trailingFlags)
            val decoded = when (compression) {
                1 -> recordBytes
                2 -> decompressPalmDoc(recordBytes)
                else -> throw ConversionException(ConversionReason.UNSUPPORTED, "HUFF/CDIC compression is not supported")
            }
            textBuilder.append(String(decoded, encoding))
        }

        val cover = if (coverIndex >= 0 && resourceStart != Int.MAX_VALUE) {
            val imgRecordIndex = resourceStart + coverIndex
            if (imgRecordIndex in 0 until numRecords) {
                val range = recordRange(imgRecordIndex)
                bytes.copyOfRange(range.first, range.last + 1)
            } else {
                null
            }
        } else {
            null
        }

        val html = convertPalmDocMarkup(textBuilder.toString())
        return ConvertedBook(html = html, title = title, author = author, cover = cover)
    }

    /** `<mbp:pagebreak>` becomes `<hr>`; everything else is treated as already-HTML-ish PalmDOC markup. */
    private fun convertPalmDocMarkup(text: String): String =
        text.replace(Regex("<mbp:pagebreak[^>]*/?>", RegexOption.IGNORE_CASE), "<hr>")

    /**
     * Strips the trailing entries [flags] declares from the end of a text record: for each set
     * bit above bit 0, an entry whose size (it included) is a backward varint in its last bytes;
     * then, for bit 0, the multibyte-overlap bytes, (last byte & 3) + 1 of them. As calibre does.
     */
    private fun stripTrailingEntries(record: ByteArray, flags: Int): ByteArray {
        if (flags == 0) return record
        var end = record.size
        var bits = flags shr 1
        while (bits != 0) {
            if ((bits and 1) != 0) end = (end - trailingEntrySize(record, end)).coerceAtLeast(0)
            bits = bits shr 1
        }
        if ((flags and 1) != 0 && end > 0) {
            end = (end - ((record[end - 1].toInt() and 0x3) + 1)).coerceAtLeast(0)
        }
        return record.copyOfRange(0, end)
    }

    private fun trailingEntrySize(record: ByteArray, end: Int): Int {
        var size = 0
        for (i in (end - 4).coerceAtLeast(0) until end) {
            val byte = record[i].toInt() and 0xFF
            if ((byte and 0x80) != 0) size = 0
            size = (size shl 7) or (byte and 0x7F)
        }
        return size
    }

    /** PalmDOC LZ77: literals, runs of 1-8 literal bytes, back-references, and space pairs. */
    internal fun decompressPalmDoc(input: ByteArray): ByteArray {
        var out = ByteArray(maxOf(input.size * 8, 4096))
        var size = 0
        fun put(b: Int) {
            if (size == out.size) out = out.copyOf(out.size * 2)
            out[size++] = b.toByte()
        }
        var i = 0
        while (i < input.size) {
            val byte = input[i++].toInt() and 0xFF
            when (byte) {
                0 -> put(0)
                in 1..8 -> repeat(byte) { if (i < input.size) put(input[i++].toInt() and 0xFF) }
                in 0x09..0x7F -> put(byte)
                in 0x80..0xBF -> {
                    if (i >= input.size) break
                    val combined = ((byte and 0x3F) shl 8) or (input[i++].toInt() and 0xFF)
                    val distance = combined shr 3
                    val length = (combined and 0x07) + 3
                    if (distance == 0 || distance > size) continue
                    // Byte by byte, so a copy that overlaps its own output (distance < length) works.
                    repeat(length) { put(out[size - distance].toInt() and 0xFF) }
                }
                else -> {
                    put(' '.code)
                    put(byte xor 0x80)
                }
            }
        }
        return out.copyOf(size)
    }
}
