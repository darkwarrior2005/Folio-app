package app.folio.reader.comic

import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** The metadata many CBZ files ship in ComicInfo.xml. */
data class ComicInfo(
    val title: String? = null,
    val series: String? = null,
    val number: Double? = null,
    val volume: Int? = null,
    val writer: String? = null,
    val publisher: String? = null,
    val year: Int? = null,
    val summary: String? = null,
    val language: String? = null,
    val pageCount: Int? = null,
    val rightToLeft: Boolean? = null,
) {
    companion object {
        fun parse(xml: String): ComicInfo? = try {
            val document = Jsoup.parse(xml, "", Parser.xmlParser())
            fun text(tag: String): String? =
                document.select(tag).firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }

            val manga = text("Manga")
            ComicInfo(
                title = text("Title"),
                series = text("Series"),
                number = text("Number")?.toDoubleOrNull(),
                volume = text("Volume")?.toIntOrNull(),
                writer = text("Writer") ?: text("Penciller") ?: text("Creator"),
                publisher = text("Publisher"),
                year = text("Year")?.toIntOrNull(),
                summary = text("Summary"),
                language = text("LanguageISO"),
                pageCount = text("PageCount")?.toIntOrNull(),
                rightToLeft = when {
                    manga.equals("YesAndRightToLeft", true) -> true
                    manga.equals("Yes", true) -> true
                    manga.equals("No", true) -> false
                    else -> null
                },
            )
        } catch (e: Exception) {
            null
        }
    }
}
