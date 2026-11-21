package app.folio.core.model

/** Trims a chapter name the way Mihon does, so names like "- Chapter 5 -" clean up consistently. */
object ChapterSanitizer {

    fun String.sanitize(title: String): String {
        return trim()
            .removePrefix(title)
            .trim(*CHAPTER_TRIM_CHARS)
    }

    private val CHAPTER_TRIM_CHARS = arrayOf(
        ' ', '\u0009', '\u000A', '\u000B', '\u000C', '\u000D', ' ', '\u0085', ' ',
        ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
        ' ', ' ', ' ', ' ', ' ', ' ', ' ', '　',
        '-', '_', ',', ':',
    ).toCharArray()
}
