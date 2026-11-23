package app.folio.reader.text.convert

import java.nio.charset.Charset

/**
 * Kindle books without DRM: MOBI (version 6), KF8 / AZW3, and the "combo" files that hold both,
 * where the KF8 part is preferred. PalmDOC-compressed and uncompressed text are read; header
 * offsets follow the format as documented by the MobileRead wiki and foliate-js (MIT).
 *
 * KF8 text is stored as page skeletons, each followed by its fragments, in reading order; it is
 * read in that order rather than reassembled, which keeps the words and their order and loses
 * only some of the publisher's wrapping markup.
 */
object MobiConverter {

    fun convert(bytes: ByteArray): ConvertedBook {
        val pdb = Pdb(bytes)
        val first = Header.read(pdb, 0)
        // Pictures are numbered from the first header's resource start, also in combo files.
        val resourceStart = first.resourceStart
        val kf8 = when {
            first.version >= 8 -> first
            first.exth.boundary != null -> runCatching { Header.read(pdb, first.exth.boundary) }.getOrNull()
                ?.takeIf { it.version >= 8 }
            else -> null
        }
        val main = kf8 ?: first

        if (main.encryption != 0) throw ConversionException(ConversionException.Reason.DRM)
        if (main.compression != 1 && main.compression != 2) {
            throw ConversionException(ConversionException.Reason.UNSUPPORTED, "compression ${main.compression}")
        }

        var text = readText(pdb, main)
        if (kf8 != null) {
            // Only the first flow is the book's text; later flows hold stylesheets and SVG.
            val flowEnd = readFdst(pdb, main)?.firstOrNull()?.second
            if (flowEnd != null && flowEnd in 1..text.size) text = text.copyOf(flowEnd)
        }
        val charset = if (main.encoding == 65001) Charsets.UTF_8 else Charset.forName("windows-1252")
        var html = String(text, charset)

        val images = ImageBudget()
        fun resource(index: Int): ByteArray? = pdb.recordOrNull(resourceStart + index)
        html = if (kf8 != null) {
            KINDLE_EMBED.replace(html) { m ->
                val index = m.groupValues[1].toIntOrNull(32)?.minus(1)
                val uri = index?.let { images.dataUri(resource(it), m.groupValues[2].ifBlank { null }) }
                uri ?: ""
            }
        } else {
            RECINDEX.replace(html) { m ->
                val index = m.groupValues[1].toIntOrNull()?.minus(1)
                val uri = index?.let { images.dataUri(resource(it)) }
                if (uri != null) "src=\"$uri\"" else ""
            }.replace(PAGE_BREAK, "<hr>")
        }

        val title = (main.exth.title ?: first.exth.title ?: main.fullName)?.trim()?.takeIf { it.isNotEmpty() }
        val author = (main.exth.authors.ifEmpty { first.exth.authors }).joinToString(", ").takeIf { it.isNotBlank() }
        val coverOffset = main.exth.coverOffset ?: first.exth.coverOffset ?: main.exth.thumbOffset
        val cover = coverOffset?.let { resource(it) }?.takeIf { ImageBudget.sniff(it) != null }
        return ConvertedBook(html, title, author, cover)
    }

    private fun readText(pdb: Pdb, header: Header): ByteArray {
        val out = java.io.ByteArrayOutputStream(header.textLength.coerceIn(0, 64 shl 20))
        for (i in 1..header.textRecordCount) {
            var record = pdb.recordOrNull(header.start + i) ?: break
            record = stripTrailing(record, header.trailingFlags)
            out.write(if (header.compression == 2) palmDoc(record) else record)
        }
        return out.toByteArray()
    }

    private fun readFdst(pdb: Pdb, header: Header): List<Pair<Int, Int>>? {
        val index = header.fdst ?: return null
        val record = pdb.recordOrNull(header.start + index) ?: return null
        if (record.size < 12 || String(record, 0, 4, Charsets.ISO_8859_1) != "FDST") return null
        val count = record.u32(8)
        return (0 until count).mapNotNull { i ->
            val at = 12 + i * 8
            if (at + 8 > record.size) null else record.u32(at) to record.u32(at + 4)
        }
    }

    /** Each text record may end with extra entries (sizes encoded backwards); they are not text. */
    internal fun stripTrailing(record: ByteArray, flags: Int): ByteArray {
        var end = record.size
        var bits = flags ushr 1
        while (bits != 0) {
            if (bits and 1 == 1) {
                var value = 0
                for (i in maxOf(0, end - 4) until end) {
                    val b = record[i].toInt() and 0xFF
                    if (b and 0x80 != 0) value = 0
                    value = (value shl 7) or (b and 0x7F)
                }
                end = (end - value).coerceAtLeast(0)
            }
            bits = bits ushr 1
        }
        if (flags and 1 == 1 && end > 0) {
            end = (end - ((record[end - 1].toInt() and 3) + 1)).coerceAtLeast(0)
        }
        return if (end == record.size) record else record.copyOf(end)
    }

    internal fun palmDoc(input: ByteArray): ByteArray {
        var buffer = ByteArray(maxOf(64, input.size * 2))
        var size = 0
        fun put(b: Int) {
            if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            buffer[size++] = b.toByte()
        }
        var i = 0
        while (i < input.size) {
            val c = input[i++].toInt() and 0xFF
            when {
                c == 0 || c in 0x09..0x7F -> put(c)
                c in 0x01..0x08 -> repeat(c) { if (i < input.size) put(input[i++].toInt() and 0xFF) }
                c in 0x80..0xBF -> {
                    if (i >= input.size) break
                    val pair = (c shl 8) or (input[i++].toInt() and 0xFF)
                    val distance = (pair and 0x3FFF) ushr 3
                    val length = (pair and 7) + 3
                    if (distance == 0 || distance > size) {
                        throw ConversionException(ConversionException.Reason.DAMAGED, "bad back-reference")
                    }
                    repeat(length) { put(buffer[size - distance].toInt()) }
                }
                else -> {
                    put(0x20)
                    put(c xor 0x80)
                }
            }
        }
        return buffer.copyOf(size)
    }

    private class Pdb(val bytes: ByteArray) {
        val offsets: IntArray

        init {
            if (bytes.size < 78 || String(bytes, 60, 8, Charsets.ISO_8859_1).let { it != "BOOKMOBI" && it != "TEXtREAd" }) {
                throw ConversionException(ConversionException.Reason.DAMAGED, "not a MOBI file")
            }
            val count = bytes.u16(76)
            offsets = IntArray(count) { bytes.u32(78 + it * 8) }
        }

        fun recordOrNull(index: Int): ByteArray? {
            if (index < 0 || index >= offsets.size) return null
            val start = offsets[index]
            val end = if (index + 1 < offsets.size) offsets[index + 1] else bytes.size
            if (start < 0 || end > bytes.size || start > end) return null
            return bytes.copyOfRange(start, end)
        }
    }

    private class Exth(
        val title: String? = null,
        val authors: List<String> = emptyList(),
        val boundary: Int? = null,
        val coverOffset: Int? = null,
        val thumbOffset: Int? = null,
    )

    private class Header(
        val start: Int,
        val compression: Int,
        val textLength: Int,
        val textRecordCount: Int,
        val encryption: Int,
        val encoding: Int,
        val version: Int,
        val fullName: String?,
        val resourceStart: Int,
        val trailingFlags: Int,
        val fdst: Int?,
        val exth: Exth,
    ) {
        companion object {
            fun read(pdb: Pdb, start: Int): Header {
                val r = pdb.recordOrNull(start) ?: throw ConversionException(ConversionException.Reason.DAMAGED, "no header")
                if (r.size < 16) throw ConversionException(ConversionException.Reason.DAMAGED, "short header")
                val compression = r.u16(0)
                val textLength = r.u32(4)
                val textRecords = r.u16(8)
                val encryption = r.u16(12)
                val hasMobi = r.size >= 24 && String(r, 16, 4, Charsets.ISO_8859_1) == "MOBI"
                if (!hasMobi) {
                    // A plain PalmDOC book: text only.
                    return Header(start, compression, textLength, textRecords, encryption, 1252, 0, null, Int.MAX_VALUE, 0, null, Exth())
                }
                val length = r.u32(20)
                val encoding = r.u32orZero(28)
                val version = r.u32orZero(36)
                val nameOffset = r.u32orZero(84)
                val nameLength = r.u32orZero(88)
                val charset = if (encoding == 65001) Charsets.UTF_8 else Charset.forName("windows-1252")
                val fullName = if (nameOffset > 0 && nameOffset + nameLength <= r.size) String(r, nameOffset, nameLength, charset) else null
                val resourceStart = r.u32orZero(108)
                val exthFlags = r.u32orZero(128)
                // The trailing-entry flags exist only in headers long enough to hold them.
                val trailing = if (length >= 0xE4 && r.size >= 244) r.u16(242) else 0
                val fdst = if (version >= 8) r.u32orZero(192).takeIf { it != -1 && it != 0 } else null
                val exth = if (exthFlags and 0x40 != 0) readExth(r, 16 + length, charset) else Exth()
                return Header(start, compression, textLength, textRecords, encryption, encoding, version, fullName, resourceStart, trailing, fdst, exth)
            }

            private fun readExth(r: ByteArray, at: Int, charset: Charset): Exth {
                if (at + 12 > r.size || String(r, at, 4, Charsets.ISO_8859_1) != "EXTH") return Exth()
                val count = r.u32(at + 8)
                var offset = at + 12
                var title: String? = null
                val authors = mutableListOf<String>()
                var boundary: Int? = null
                var cover: Int? = null
                var thumb: Int? = null
                repeat(count) {
                    if (offset + 8 > r.size) return@repeat
                    val type = r.u32(offset)
                    val size = r.u32(offset + 4)
                    if (size < 8 || offset + size > r.size) return@repeat
                    val dataAt = offset + 8
                    val dataLength = size - 8
                    fun string() = String(r, dataAt, dataLength, charset).trim()
                    fun number() = if (dataLength >= 4) r.u32(dataAt).takeIf { it != -1 } else null
                    when (type) {
                        100 -> string().takeIf { it.isNotEmpty() }?.let(authors::add)
                        121 -> boundary = number()
                        201 -> cover = number()
                        202 -> thumb = number()
                        503 -> title = string().takeIf { it.isNotEmpty() }
                    }
                    offset += size
                }
                return Exth(title, authors, boundary, cover, thumb)
            }
        }
    }

    private val KINDLE_EMBED = Regex("kindle:embed:([0-9A-Va-v]+)(?:\\?mime=([\\w/+.-]+))?")
    private val RECINDEX = Regex("recindex=[\"']?0*(\\d+)[\"']?", RegexOption.IGNORE_CASE)
    private val PAGE_BREAK = Regex("<mbp:pagebreak\\s*/?>", RegexOption.IGNORE_CASE)
}

private fun ByteArray.u16(at: Int): Int = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

private fun ByteArray.u32(at: Int): Int =
    ((this[at].toInt() and 0xFF) shl 24) or ((this[at + 1].toInt() and 0xFF) shl 16) or
        ((this[at + 2].toInt() and 0xFF) shl 8) or (this[at + 3].toInt() and 0xFF)

private fun ByteArray.u32orZero(at: Int): Int = if (at + 4 <= size) u32(at) else 0
