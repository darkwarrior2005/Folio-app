package app.folio.core.model

/**
 * Cleans a chapter name the way Mihon does: surrounding space and separators go, and so does the
 * manga's title when the source repeats it at the start of every chapter.
 *
 * Ported from Mihon (Apache-2.0).
 */
object ChapterSanitizer {

    fun String.sanitize(title: String): String {
        return trim()
            .removePrefix(title)
            .trim(*CHAPTER_TRIM_CHARS)
    }

    private val CHAPTER_TRIM_CHARS = arrayOf(
        // Whitespace
        ' ',
        '\u0009',
        '\u000A',
        '\u000B',
        '\u000C',
        '\u000D',
        ' ',
        '\u0085',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        ' ',
        '　',

        // Separators
        '-',
        '_',
        ',',
        ':',
    ).toCharArray()
}
