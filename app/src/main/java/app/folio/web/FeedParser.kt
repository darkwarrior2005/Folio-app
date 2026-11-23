package app.folio.web

import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class FeedItem(
    val id: String,
    val title: String,
    val link: String,
    val published: Long? = null,
    val summary: String? = null,
    val author: String? = null,
)

data class ParsedFeed(
    val title: String,
    val siteUrl: String?,
    val items: List<FeedItem>,
)

/** RSS 2.0, RSS 1.0 (RDF) and Atom feeds, and finding a site's feed from its home page. */
object FeedParser {

    fun parse(xml: String, feedUrl: String): ParsedFeed? {
        val doc = runCatching { Jsoup.parse(xml, feedUrl, Parser.xmlParser()) }.getOrNull() ?: return null
        val root = doc.children().firstOrNull() ?: return null
        return when (root.tagName().substringAfter(':').lowercase()) {
            "rss" -> rss(root.selectFirst("channel") ?: return null, root.select("item"), feedUrl)
            "rdf" -> rss(root.selectFirst("channel") ?: root, root.select("item"), feedUrl)
            "feed" -> atom(root, feedUrl)
            else -> null
        }
    }

    /** Feed addresses a web page advertises with `<link rel="alternate">`, best first. */
    fun discover(html: String, pageUrl: String): List<String> {
        val doc = Jsoup.parse(html, pageUrl)
        return doc.select("link[rel=alternate][href]")
            .filter { link ->
                val type = link.attr("type").lowercase()
                type.contains("rss") || type.contains("atom")
            }
            .mapNotNull { ArticleExtractor.resolve(pageUrl, it.attr("href")) }
            .distinct()
    }

    private fun rss(channel: Element, items: List<Element>, feedUrl: String): ParsedFeed {
        val title = channel.child("title")?.text()?.trim().orEmpty().ifEmpty { hostOf(feedUrl) }
        val site = channel.child("link")?.text()?.trim()?.let { ArticleExtractor.resolve(feedUrl, it) }
        return ParsedFeed(
            title = title,
            siteUrl = site,
            items = items.mapNotNull { item ->
                val link = (item.child("link")?.text()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: item.child("guid")?.takeIf { it.attr("isPermaLink") != "false" }?.text()?.trim())
                    ?.let { ArticleExtractor.resolve(feedUrl, it) }
                    ?: return@mapNotNull null
                val id = item.child("guid")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: link
                FeedItem(
                    id = id,
                    title = item.child("title")?.text()?.let(::plain)?.ifBlank { null } ?: link,
                    link = link,
                    published = (item.child("pubDate") ?: item.child("dc:date") ?: item.child("date"))?.text()?.let(::date),
                    summary = item.child("description")?.text()?.let(::plain)?.let(::shorten),
                    author = (item.child("dc:creator") ?: item.child("author") ?: item.child("creator"))?.text()?.trim()?.ifBlank { null },
                )
            },
        )
    }

    private fun atom(feed: Element, feedUrl: String): ParsedFeed {
        val title = feed.child("title")?.text()?.trim().orEmpty().ifEmpty { hostOf(feedUrl) }
        val site = feed.children().filter { it.tagName() == "link" }
            .firstOrNull { it.attr("rel").let { rel -> rel.isEmpty() || rel == "alternate" } }
            ?.attr("href")?.let { ArticleExtractor.resolve(feedUrl, it) }
        return ParsedFeed(
            title = title,
            siteUrl = site,
            items = feed.children().filter { it.tagName() == "entry" }.mapNotNull { entry ->
                val links = entry.children().filter { it.tagName() == "link" }
                val href = (links.firstOrNull { it.attr("rel").let { rel -> rel.isEmpty() || rel == "alternate" } } ?: links.firstOrNull())
                    ?.attr("href")?.let { ArticleExtractor.resolve(feedUrl, it) }
                    ?: return@mapNotNull null
                FeedItem(
                    id = entry.child("id")?.text()?.trim()?.ifBlank { null } ?: href,
                    title = entry.child("title")?.text()?.let(::plain)?.ifBlank { null } ?: href,
                    link = href,
                    published = (entry.child("published") ?: entry.child("updated"))?.text()?.let(::date),
                    summary = (entry.child("summary") ?: entry.child("content"))?.text()?.let(::plain)?.let(::shorten),
                    author = entry.child("author")?.let { it.child("name")?.text() ?: it.text() }?.trim()?.ifBlank { null },
                )
            },
        )
    }

    private fun Element.child(name: String): Element? = children().firstOrNull { it.tagName().equals(name, ignoreCase = true) }

    /** Feed text is often escaped HTML; keep only its words. */
    private fun plain(text: String): String = Jsoup.parse(text).text().trim()

    private fun shorten(text: String): String? = text.takeIf { it.isNotBlank() }?.let { if (it.length > 280) it.take(277).trimEnd() + "…" else it }

    private fun hostOf(url: String): String = runCatching { java.net.URI.create(url).host?.removePrefix("www.") }.getOrNull() ?: url

    internal fun date(text: String): Long? {
        val value = text.trim()
        runCatching { return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        // RFC 822 with a named zone or without seconds, as many feeds write it.
        for (pattern in listOf("EEE, d MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm zzz", "d MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd")) {
            runCatching {
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
                return if (pattern == "yyyy-MM-dd") {
                    java.time.LocalDate.parse(value.take(10), formatter).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                } else {
                    ZonedDateTime.parse(value, formatter).toInstant().toEpochMilli()
                }
            }
        }
        return null
    }
}
