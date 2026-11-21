package app.folio.reader.text.convert

/**
 * A small RTF tokenizer: groups, control words, \uN with \ucN skipping, \'hh in the \ansicpg
 * code page, \par/\line, bold/italic/underline, and \info's title/author. Skips font/colour/style
 * tables, \* destinations, pictures, headers/footers and field instructions.
 */
object RtfConverter {

    fun convert(bytes: ByteArray): ConvertedBook {
        val text = String(bytes, Charsets.ISO_8859_1)
        if (!text.trimStart().startsWith("{\\rtf")) {
            throw ConversionException(ConversionReason.UNSUPPORTED, "Not an RTF file")
        }
        val parser = Parser(text)
        return parser.parse()
    }

    private class Parser(private val text: String) {
        var pos = 0
        var codePage = 1252
        var ucSkip = 1
        var title: String? = null
        var author: String? = null
        val html = StringBuilder("<p>")
        var paragraphOpen = true
        var bold = false
        var italic = false
        var underline = false

        // Nesting depth at which we entered a destination we should skip entirely
        // (fonttbl, colortbl, stylesheet, a \* unknown destination, pict, header/footer, field).
        var skipDepth = -1
        var depth = 0

        fun parse(): ConvertedBook {
            while (pos < text.length) {
                when (val c = text[pos]) {
                    '{' -> { depth++; pos++ }
                    '}' -> {
                        if (depth == skipDepth) skipDepth = -1
                        depth--
                        pos++
                    }
                    '\\' -> parseControl()
                    '\r', '\n' -> pos++
                    else -> {
                        if (skipDepth == -1) appendChar(c)
                        pos++
                    }
                }
            }
            if (paragraphOpen) html.append("</p>")
            return ConvertedBook(html = html.toString(), title = title, author = author)
        }

        fun appendChar(c: Char) {
            when (c) {
                '<' -> html.append("&lt;")
                '>' -> html.append("&gt;")
                '&' -> html.append("&amp;")
                else -> html.append(c)
            }
        }

        fun parseControl() {
            pos++ // consume backslash
            if (pos >= text.length) return
            val c = text[pos]
            if (!c.isLetter()) {
                // Escaped literal char, e.g. \\ \{ \}
                when (c) {
                    '\'' -> {
                        val hex = text.substring(pos + 1, (pos + 3).coerceAtMost(text.length))
                        val byte = hex.toIntOrNull(16)
                        pos += 3
                        if (byte != null && skipDepth == -1) {
                            appendChar(String(byteArrayOf(byte.toByte()), charset(codePageName())).firstOrNull() ?: '?')
                        }
                        return
                    }
                    '{', '}', '\\' -> { if (skipDepth == -1) appendChar(c); pos++; return }
                    else -> { pos++; return }
                }
            }
            val word = buildString { while (pos < text.length && text[pos].isLetter()) { append(text[pos]); pos++ } }
            val negative = pos < text.length && text[pos] == '-'
            if (negative) pos++
            val numStart = pos
            while (pos < text.length && text[pos].isDigit()) pos++
            val num = text.substring(numStart, pos).toIntOrNull()?.let { if (negative) -it else it }
            if (pos < text.length && text[pos] == ' ') pos++ // the one delimiting space is consumed

            when (word) {
                "par" -> { if (paragraphOpen) html.append("</p>"); html.append("<p>"); paragraphOpen = true }
                "line" -> html.append("<br>")
                "tab" -> html.append("&emsp;")
                "b" -> setStyle("b", num != 0) { bold = it }
                "i" -> setStyle("i", num != 0) { italic = it }
                "ul" -> setStyle("u", num != 0) { underline = it }
                "ulnone" -> setStyle("u", false) { underline = it }
                "ansicpg" -> num?.let { codePage = it }
                "uc" -> num?.let { ucSkip = it }
                "u" -> {
                    num?.let { if (skipDepth == -1) appendChar(it.toChar()) }
                    repeat(ucSkip) { skipOneWordOrChar() }
                }
                "fonttbl", "colortbl", "stylesheet", "pict", "header", "footer", "field", "object" -> {
                    if (skipDepth == -1) skipDepth = depth
                }
                "*" -> if (skipDepth == -1) skipDepth = depth
                "title" -> title = readDestinationText()
                "author" -> author = readDestinationText()
            }
        }

        private fun setStyle(tag: String, on: Boolean, set: (Boolean) -> Unit) {
            set(on)
            if (skipDepth == -1) html.append(if (on) "<$tag>" else "</$tag>")
        }

        private fun skipOneWordOrChar() {
            if (pos < text.length) {
                if (text[pos] == '\\') { parseControl(); return }
                pos++
            }
        }

        /** Reads plain text until the enclosing group closes, for \title/\author destinations. */
        private fun readDestinationText(): String {
            val start = pos
            var localDepth = 0
            val builder = StringBuilder()
            while (pos < text.length) {
                when (text[pos]) {
                    '{' -> { localDepth++; pos++ }
                    '}' -> { if (localDepth == 0) return builder.toString().trim(); localDepth--; pos++ }
                    '\\' -> {
                        val before = pos
                        parseControl()
                        if (pos == before) pos++
                    }
                    else -> { builder.append(text[pos]); pos++ }
                }
            }
            return builder.toString().trim()
        }

        private fun codePageName(): String = when (codePage) {
            1250 -> "windows-1250"
            1251 -> "windows-1251"
            1252 -> "windows-1252"
            1253 -> "windows-1253"
            1254 -> "windows-1254"
            else -> "windows-1252"
        }
    }
}
