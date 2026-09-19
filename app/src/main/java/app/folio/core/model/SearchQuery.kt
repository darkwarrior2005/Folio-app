package app.folio.core.model

/** Builds safe SQLite FTS4 MATCH expressions from whatever the user types. */
object SearchQuery {

    private val TOKEN = Regex("[\\p{L}\\p{N}_]+")

    /**
     * "machine learn" becomes `"machine" "learn"*`, so every word must appear and the last word
     * matches as a prefix while the user is still typing. Returns null when there is nothing to
     * search for, which callers treat as "no results".
     */
    fun toFtsMatch(raw: String): String? {
        val tokens = TOKEN.findAll(raw).map { it.value }.toList()
        if (tokens.isEmpty()) return null
        return tokens.mapIndexed { index, token ->
            val quoted = "\"$token\""
            if (index == tokens.lastIndex && token.length >= 2) "$quoted*" else quoted
        }.joinToString(" ")
    }

    /** Strip the markers used by the snippet() call so the UI can highlight them itself. */
    fun parseSnippet(snippet: String): List<Pair<String, Boolean>> {
        val out = mutableListOf<Pair<String, Boolean>>()
        var rest = snippet
        while (true) {
            val start = rest.indexOf(OPEN)
            if (start < 0) break
            val end = rest.indexOf(CLOSE, start)
            if (end < 0) break
            if (start > 0) out += rest.substring(0, start) to false
            out += rest.substring(start + OPEN.length, end) to true
            rest = rest.substring(end + CLOSE.length)
        }
        if (rest.isNotEmpty()) out += rest to false
        return out
    }

    const val OPEN = "[["
    const val CLOSE = "]]"
}
