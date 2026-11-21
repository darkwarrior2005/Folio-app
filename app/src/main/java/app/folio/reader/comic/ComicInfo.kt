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
    /** Minimal ComicInfo.xml a downloaded manga chapter ships, so other readers recognize it too. */
    fun toXml(): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""")
        title?.let { appendLine("  <Title>${escape(it)}</Title>") }
        series?.let { appendLine("  <Series>${escape(it)}</Series>") }
        number?.let { appendLine("  <Number>$it</Number>") }
        writer?.let { appendLine("  <Writer>${escape(it)}</Writer>") }
        summary?.let { appendLine("  <Summary>${escape(it)}</Summary>") }
        pageCount?.let { appendLine("  <PageCount>$it</PageCount>") }
        if (rightToLeft == true) appendLine("  <Manga>YesAndRightToLeft</Manga>")
        appendLine("</ComicInfo>")
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

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
