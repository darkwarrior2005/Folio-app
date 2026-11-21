package app.folio.reader.text.convert

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

private val WINDOWS_1252: Charset = Charset.forName("windows-1252")

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
        val record0Buf = ByteBuffer.wrap(bytes, record0.first, record0.last - record0.first).order(ByteOrder.BIG_ENDIAN)
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

        val hasMobiHeader = record0.last - record0.first > 16 && String(bytes, record0.first + 16, 4) == "MOBI"
        if (hasMobiHeader) {
            val mobiOffset = record0.first + 16
            val mobiBuf = ByteBuffer.wrap(bytes, mobiOffset, record0.last - mobiOffset).order(ByteOrder.BIG_ENDIAN)
            val encodingValue = mobiBuf.getInt(16) // offset 16 within MOBI header = text encoding
            encoding = if (encodingValue == 65001) Charsets.UTF_8 else WINDOWS_1252
            resourceStart = mobiBuf.getInt(108)
            val exthFlags = mobiBuf.getInt(128)
            val headerLength = mobiBuf.getInt(0 + 4) // length field at MOBI header offset 4
            if ((exthFlags and 0x40) != 0) {
                val exthOffset = mobiOffset + headerLength
                if (exthOffset + 12 <= bytes.size && String(bytes, exthOffset, 4) == "EXTH") {
                    val exthBuf = ByteBuffer.wrap(bytes, exthOffset, bytes.size - exthOffset).order(ByteOrder.BIG_ENDIAN)
                    val recCount = exthBuf.getInt(8)
                    var pos = 12
                    repeat(recCount) {
                        if (exthOffset + pos + 8 > bytes.size) return@repeat
                        val recType = exthBuf.getInt(pos)
                        val recLen = exthBuf.getInt(pos + 4)
                        val dataStart = exthOffset + pos + 8
                        val dataLen = (recLen - 8).coerceAtLeast(0)
                        if (dataStart + dataLen <= bytes.size) {
                            when (recType) {
                                100 -> author = String(bytes, dataStart, dataLen, Charsets.UTF_8)
                                503 -> title = String(bytes, dataStart, dataLen, Charsets.UTF_8)
                                201 -> coverIndex = ByteBuffer.wrap(bytes, dataStart, dataLen).order(ByteOrder.BIG_ENDIAN).int
                            }
                        }
                        pos += recLen
                    }
                }
            }
            if (title == null) {
                val fullNameOffset = mobiBuf.getInt(84)
                val fullNameLength = mobiBuf.getInt(88)
                if (fullNameOffset > 0 && mobiOffset + fullNameOffset + fullNameLength <= bytes.size) {
                    title = String(bytes, mobiOffset + fullNameOffset, fullNameLength, Charsets.UTF_8)
                }
            }
        }

        // Trailing-entry flags (MOBI header offset 242, relative to mobiOffset) say how many
        // bytes of each text record are backward-varint trailing data to strip before decompressing.
        val trailingFlags = if (hasMobiHeader) {
            val mobiOffset = record0.first + 16
            if (mobiOffset + 242 + 2 <= bytes.size) {
                ByteBuffer.wrap(bytes, mobiOffset + 242, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
            } else {
                0
            }
        } else {
            0
        }

        val textBuilder = StringBuilder()
        for (i in 1..recordCount) {
            if (i >= numRecords) break
            val range = recordRange(i)
            var recordBytes = bytes.copyOfRange(range.first, range.last)
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
                bytes.copyOfRange(range.first, range.last)
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

    private fun stripTrailingEntries(record: ByteArray, flags: Int): ByteArray {
        if (flags == 0) return record
        var end = record.size
        // Multibyte overlap flag (bit 0) is handled innermost; count down from the highest bit.
        for (bit in 7 downTo 1) {
            if ((flags and (1 shl bit)) != 0) {
                end = removeTrailingEntry(record, end)
            }
        }
        if ((flags and 1) != 0) {
            end = removeMultibyteTrailingEntry(record, end)
        }
        return record.copyOfRange(0, end)
    }

    private fun removeTrailingEntry(record: ByteArray, end: Int): Int {
        var length = 0
        var pos = end
        for (shift in intArrayOf(0, 7, 14, 21)) {
            pos--
            if (pos < 0) break
            val byte = record[pos].toInt() and 0xFF
            length = length or ((byte and 0x7F) shl shift)
            if ((byte and 0x80) != 0) break
        }
        return (end - length).coerceAtLeast(0)
    }

    private fun removeMultibyteTrailingEntry(record: ByteArray, end: Int): Int {
        val newEnd = removeTrailingEntry(record, end)
        // The multibyte flag's length already includes its own size byte(s); nothing further to do.
        return newEnd
    }

    /** PalmDOC LZ77-ish decompression: literal bytes, back-references, and space-pairs. */
    private fun decompressPalmDoc(input: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(input.size * 3)
        var i = 0
        while (i < input.size) {
            val byte = input[i].toInt() and 0xFF
            i++
            when {
                byte == 0 -> out.write(byte)
                byte in 1..8 -> {
                    val count = byte
                    repeat(count) { if (i < input.size) out.write(input[i++].toInt() and 0xFF) }
                }
                byte <= 0x7F -> out.write(byte)
                byte in 0xC0..0xFF -> {
                    out.write(' '.code)
                    out.write(byte xor 0x80)
                }
                else -> { // 0x80..0xBF: back-reference
                    if (i >= input.size) break
                    val next = input[i].toInt() and 0xFF
                    i++
                    val combined = ((byte and 0x3F) shl 8) or next
                    val distance = combined shr 3
                    val length = (combined and 0x07) + 3
                    val existing = out.toByteArray()
                    val start = (existing.size - distance).coerceAtLeast(0)
                    for (k in 0 until length) {
                        val sourceIndex = start + k
                        if (sourceIndex in existing.indices) out.write(existing[sourceIndex].toInt())
                    }
                }
            }
        }
        return out.toByteArray()
    }
}
