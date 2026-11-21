package app.folio.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class FeedKind { RSS2, RSS1, ATOM }

data class FeedItem(
    val title: String,
    val link: String,
    val summary: String?,
    val publishedAt: Long?,
)

data class ParsedFeed(
    val kind: FeedKind,
    val title: String?,
    val siteUrl: String?,
    val items: List<FeedItem>,
)

/** Parses RSS 2.0, RSS 1.0 (RDF) and Atom; strips HTML from summaries. */
object FeedParser {

    fun parse(xml: String): ParsedFeed {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        return when {
            document.selectFirst("rss") != null -> parseRss2(document)
            document.selectFirst("feed") != null -> parseAtom(document)
            document.selectFirst("RDF") != null || document.selectFirst("rdf\\:RDF") != null -> parseRss1(document)
            else -> throw IllegalArgumentException("Not a recognizable feed")
        }
    }

    private fun parseRss2(document: Document): ParsedFeed {
        val channel = document.selectFirst("channel")
        val items = (channel ?: document).select("item").map { item ->
            FeedItem(
                title = item.selectFirst("title")?.text().orEmpty(),
                link = item.selectFirst("link")?.text().orEmpty(),
                summary = stripHtml(item.selectFirst("description")?.text()),
                publishedAt = parseRfc822(item.selectFirst("pubDate")?.text()),
            )
        }.filter { it.link.isNotBlank() }
        return ParsedFeed(
            FeedKind.RSS2,
            channel?.selectFirst("title")?.text(),
            channel?.selectFirst("link")?.text(),
            items,
        )
    }

    private fun parseRss1(document: Document): ParsedFeed {
        val items = document.select("item").map { item ->
            FeedItem(
                title = item.selectFirst("title")?.text().orEmpty(),
                link = item.selectFirst("link")?.text().orEmpty(),
                summary = stripHtml(item.selectFirst("description")?.text()),
                publishedAt = parseIso(item.selectFirst("date")?.text()),
            )
        }.filter { it.link.isNotBlank() }
        val channel = document.selectFirst("channel")
        return ParsedFeed(FeedKind.RSS1, channel?.selectFirst("title")?.text(), channel?.selectFirst("link")?.text(), items)
    }

    private fun parseAtom(document: Document): ParsedFeed {
        val feed = document.selectFirst("feed")
        val items = (feed ?: document).select("entry").map { entry ->
            val link = entry.select("link").firstOrNull { it.attr("rel").let { rel -> rel.isBlank() || rel == "alternate" } }
                ?.attr("href").orEmpty()
            FeedItem(
                title = entry.selectFirst("title")?.text().orEmpty(),
                link = link,
                summary = stripHtml(entry.selectFirst("summary")?.text() ?: entry.selectFirst("content")?.text()),
                publishedAt = parseIso(entry.selectFirst("updated")?.text() ?: entry.selectFirst("published")?.text()),
            )
        }.filter { it.link.isNotBlank() }
        val selfLink = feed?.select("link")?.firstOrNull { it.attr("rel") == "alternate" || it.attr("rel").isBlank() }?.attr("href")
        return ParsedFeed(FeedKind.ATOM, feed?.selectFirst("title")?.text(), selfLink, items)
    }

    /** Reads a page's `<link rel="alternate" type="...rss/atom">` to find its feed address. */
    fun discover(html: String, baseUrl: String): String? {
        val document = Jsoup.parse(html, baseUrl)
        return document.select("link[rel=alternate]").firstOrNull { link ->
            val type = link.attr("type").lowercase()
            "rss" in type || "atom" in type || "xml" in type
        }?.let { ArticleExtractor.resolve(baseUrl, it.attr("href")) }
    }

    private fun stripHtml(html: String?): String? = html?.let { Jsoup.parse(it).text().takeIf { t -> t.isNotBlank() } }

    private fun parseRfc822(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val formats = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "dd MMM yyyy HH:mm:ss Z")
        for (format in formats) {
            try {
                return SimpleDateFormat(format, Locale.US).parse(value.trim())?.time
            } catch (e: Exception) {
                // try the next format
            }
        }
        return null
    }

    private fun parseIso(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(value.trim())).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
