package app.folio.reader.text.convert

import java.nio.charset.Charset

/**
 * Rich Text Format (.rtf): paragraphs, line breaks, bold, italic, underline, Unicode and code-page
 * characters, and the title and author from the info group. Font, colour and style tables,
 * pictures, headers, footers and field instructions are skipped.
 */
object RtfConverter {

    fun convert(bytes: ByteArray): ConvertedBook {
        val text = String(bytes, Charsets.ISO_8859_1)
        if (!text.trimStart().startsWith("{\\rtf")) throw ConversionException(ConversionException.Reason.DAMAGED, "not RTF")
        return Parser(text).parse()
    }

    private class State(
        var bold: Boolean = false,
        var italic: Boolean = false,
        var underline: Boolean = false,
        var skip: Boolean = false,
        var unicodeSkip: Int = 1,
        var destination: String? = null,
    ) {
        fun copy() = State(bold, italic, underline, skip, unicodeSkip, destination)
    }

    private class Parser(val src: String) {
        var i = 0
        val html = StringBuilder()
        val paragraph = StringBuilder()
        var open = State() // the formatting the paragraph's open tags reflect
        var state = State()
        val stack = ArrayDeque<State>()
        var charset: Charset = Charset.forName("windows-1252")
        var pendingBytes = java.io.ByteArrayOutputStream()
        var skipChars = 0
        var title: String? = null
        var author: String? = null
        val info = StringBuilder()

        fun parse(): ConvertedBook {
            while (i < src.length) {
                val c = src[i]
                when (c) {
                    '{' -> {
                        flushBytes()
                        stack.addLast(state.copy())
                        state = state.copy()
                        i++
                        // "{\*\dest ...}" is an optional destination: ignorable unless known.
                        if (src.startsWith("\\*", i)) {
                            state.skip = true
                            i += 2
                        }
                    }
                    '}' -> {
                        flushBytes()
                        endDestination()
                        state = stack.removeLastOrNull() ?: state
                        i++
                    }
                    '\\' -> control()
                    '\r', '\n' -> i++
                    else -> {
                        flushBytes()
                        text(c.toString())
                        i++
                    }
                }
            }
            flushBytes()
            endParagraph()
            return ConvertedBook(html.toString(), title, author, null)
        }

        private fun endDestination() {
            when (state.destination) {
                "title" -> title = info.toString().trim().takeIf { it.isNotEmpty() }
                "author" -> author = info.toString().trim().takeIf { it.isNotEmpty() }
            }
            if (state.destination != null) info.setLength(0)
        }

        private fun control() {
            i++ // the backslash
            if (i >= src.length) return
            val c = src[i]
            if (!c.isLetter()) {
                i++
                when (c) {
                    '\'' -> {
                        val hex = src.substring(i, minOf(i + 2, src.length))
                        i += hex.length
                        hex.toIntOrNull(16)?.let { if (!consumeSkip()) pendingBytes.write(it) }
                    }
                    '~' -> text(" ")
                    '-', '*' -> Unit
                    '_' -> text("-")
                    '\n', '\r' -> { flushBytes(); endParagraph() }
                    else -> text(c.toString()) // \\ \{ \}
                }
                return
            }
            flushBytes()
            val start = i
            while (i < src.length && src[i].isLetter()) i++
            val word = src.substring(start, i)
            val numberStart = i
            if (i < src.length && (src[i] == '-' || src[i].isDigit())) {
                i++
                while (i < src.length && src[i].isDigit()) i++
            }
            val param = src.substring(numberStart, i).toIntOrNull()
            if (i < src.length && src[i] == ' ') i++ // the delimiter belongs to the control word

            when (word) {
                "par", "sect", "page" -> endParagraph()
                "line" -> text("<br>", raw = true)
                "tab" -> text(" ")
                "emdash" -> text("—")
                "endash" -> text("–")
                "lquote" -> text("‘")
                "rquote" -> text("’")
                "ldblquote" -> text("“")
                "rdblquote" -> text("”")
                "bullet" -> text("•")
                "b" -> state.bold = param != 0
                "i" -> state.italic = param != 0
                "ul" -> state.underline = param != 0
                "ulnone" -> state.underline = false
                "plain" -> { state.bold = false; state.italic = false; state.underline = false }
                "uc" -> state.unicodeSkip = param ?: 1
                "u" -> param?.let {
                    val code = if (it < 0) it + 65536 else it
                    text(String(Character.toChars(code)))
                    skipChars = state.unicodeSkip
                }
                "ansicpg" -> param?.let { cp -> runCatching { Charset.forName("windows-$cp") }.getOrNull()?.let { charset = it } }
                "title", "author" -> {
                    state.destination = word
                    state.skip = false
                    info.setLength(0)
                }
                "fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "headerl", "headerr",
                "footerl", "footerr", "fldinst", "listtable", "listoverridetable", "revtbl", "rsidtbl",
                "generator", "xmlnstbl", "themedata", "colorschememapping", "latentstyles", "datastore",
                "object", "footnote", "bkmkstart", "bkmkend", "operator", "company", "doccomm",
                -> state.skip = true
            }
        }

        private fun consumeSkip(): Boolean {
            if (skipChars > 0) {
                skipChars--
                return true
            }
            return false
        }

        private fun flushBytes() {
            if (pendingBytes.size() == 0) return
            val decoded = String(pendingBytes.toByteArray(), charset)
            pendingBytes = java.io.ByteArrayOutputStream()
            text(decoded)
        }

        private fun text(value: String, raw: Boolean = false) {
            if (!raw && consumeSkip()) {
                if (value.length > 1) text(value.substring(1))
                return
            }
            if (state.destination != null) {
                info.append(value)
                return
            }
            if (state.skip) return
            if (state.bold != open.bold || state.italic != open.italic || state.underline != open.underline) {
                closeTags()
                if (state.bold) paragraph.append("<strong>")
                if (state.italic) paragraph.append("<em>")
                if (state.underline) paragraph.append("<u>")
                open = State(state.bold, state.italic, state.underline)
            }
            paragraph.append(if (raw) value else escape(value))
        }

        private fun closeTags() {
            if (open.underline) paragraph.append("</u>")
            if (open.italic) paragraph.append("</em>")
            if (open.bold) paragraph.append("</strong>")
            open = State()
        }

        private fun endParagraph() {
            closeTags()
            val content = paragraph.toString()
            paragraph.setLength(0)
            if (content.replace("<br>", "").isNotBlank()) html.append("<p>").append(content).append("</p>")
        }
    }
}
