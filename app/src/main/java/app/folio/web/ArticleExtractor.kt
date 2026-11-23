package app.folio.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** The readable part of a web page. [contentHtml] has absolute links and picture addresses. */
data class Article(
    val url: String,
    val title: String,
    val author: String?,
    val site: String?,
    val contentHtml: String,
    val imageUrls: List<String>,
    /** The page's "next" link (next chapter, next page), when it has one on the same site. */
    val next: String?,
)

/**
 * Finds the article in a web page the way reader views do: the block holding the most paragraph
 * text wins, with menus, comments, share bars and adverts taken away first. Works on plain HTML
 * only; pages built entirely by scripts have little to find.
 */
object ArticleExtractor {

    fun extract(html: String, url: String): Article {
        val doc = Jsoup.parse(html, url)
        val title = title(doc)
        val author = author(doc)
        val site = meta(doc, "og:site_name") ?: host(url)
        val next = nextLink(doc, url)

        doc.select(REMOVE).remove()
        doc.allElements.filter { it !== doc.body() && it.isNoise() }.forEach { it.remove() }

        val content = mainContent(doc)
        content.select("img").forEach { img ->
            val src = sequenceOf("data-src", "data-lazy-src", "data-original", "data-srcset", "srcset", "src")
                .map { img.attr(it).trim() }
                .firstOrNull { it.isNotEmpty() && !it.startsWith("data:") }
                ?.substringBefore(' ')?.substringBefore(',')
            val absolute = src?.let { resolve(url, it) }
            val tiny = img.attr("width") == "1" || img.attr("height") == "1"
            if (absolute == null || tiny) {
                img.remove()
            } else {
                img.clearAttributes()
                img.attr("src", absolute)
            }
        }
        content.select("a[href]").forEach { a ->
            val absolute = resolve(url, a.attr("href"))
            if (absolute == null) a.unwrap() else a.attr("href", absolute)
        }
        // The title is shown once, above the text.
        content.select("h1").firstOrNull()?.takeIf { it.text().trim().equals(title, ignoreCase = true) }?.remove()
        content.allElements.reversed().forEach { e ->
            if (e !== content && e.tagName() in EMPTY_REMOVABLE && e.text().isBlank() && e.select("img").isEmpty()) e.remove()
        }

        val images = content.select("img").map { it.attr("src") }.distinct()
        // One element per paragraph, heading or picture, so the reader can keep a precise place.
        val blocks = if (content.textNodes().any { it.text().isNotBlank() }) listOf(content) else content.children().flatMap { it.flattened() }
        return Article(url, title, author, site, blocks.joinToString("\n") { it.outerHtml() }, images, next)
    }

    /** A same-site "next" link: `rel=next`, or a link whose words say next in common languages. */
    fun nextLink(doc: Document, url: String): String? {
        val candidates = sequence {
            yieldAll(doc.select("link[rel=next], a[rel=next]").map { it.attr("href") })
            yieldAll(
                doc.select("a[href]").filter { a ->
                    val text = a.text().trim().lowercase()
                    val label = (a.attr("title") + " " + a.attr("aria-label")).lowercase()
                    NEXT_TEXT.matches(text) || NEXT_TEXT.matches(label.trim()) ||
                        (text.length < 30 && NEXT_CLASS.containsMatchIn(a.className() + " " + a.id()))
                }.map { it.attr("href") },
            )
        }
        val here = runCatching { URI.create(url.substringBefore('#')) }.getOrNull() ?: return null
        return candidates
            .mapNotNull { resolve(url, it) }
            .firstOrNull { candidate ->
                val target = runCatching { URI.create(candidate) }.getOrNull() ?: return@firstOrNull false
                target.host.equals(here.host, ignoreCase = true) &&
                    candidate.substringBefore('#') != url.substringBefore('#')
            }
    }

    private fun mainContent(doc: Document): Element {
        doc.selectFirst("[itemprop=articleBody]")?.takeIf { it.text().length > 200 }?.let { return it }

        val scores = HashMap<Element, Double>()
        doc.select("p, pre, td, blockquote, li").forEach { p ->
            val text = p.ownText().trim().ifEmpty { p.text().trim() }
            if (text.length < 25) return@forEach
            val score = 1.0 + text.count { it == ',' || it == '，' || it == '、' } + minOf(text.length / 100.0, 3.0)
            val parent = p.parent() ?: return@forEach
            scores[parent] = (scores[parent] ?: parent.weight()) + score
            parent.parent()?.let { grand -> scores[grand] = (scores[grand] ?: grand.weight()) + score / 2 }
        }
        val best = scores.entries
            .map { (element, score) -> element to score * (1 - element.linkDensity()) }
            .maxByOrNull { it.second }
            ?.first
            ?: doc.selectFirst("article, main") ?: doc.body()

        // Readability keeps siblings that score nearly as well, which brings back split articles.
        val parent = best.parent()
        val bestScore = scores[best] ?: 0.0
        if (parent != null && bestScore > 0) {
            val kept = parent.children().filter { sibling ->
                sibling === best || (scores[sibling] ?: 0.0) >= bestScore * 0.2 && sibling.linkDensity() < 0.25
            }
            if (kept.size > 1) {
                val wrapper = Element("div")
                kept.forEach { wrapper.appendChild(it.clone()) }
                return wrapper
            }
        }
        return best
    }

    private fun Element.flattened(): List<Element> {
        if (tagName() !in CONTAINERS || children().none { it.isBlock }) return listOf(this)
        if (textNodes().any { it.text().isNotBlank() }) return listOf(this)
        return children().flatMap { it.flattened() }
    }

    private fun Element.weight(): Double {
        val names = (className() + " " + id()).lowercase()
        var weight = 0.0
        if (tagName() == "article" || tagName() == "main") weight += 25
        if (POSITIVE.containsMatchIn(names)) weight += 25
        if (NEGATIVE.containsMatchIn(names)) weight -= 25
        return weight
    }

    private fun Element.linkDensity(): Double {
        val length = text().length
        if (length == 0) return 0.0
        return select("a").sumOf { it.text().length }.toDouble() / length
    }

    private fun Element.isNoise(): Boolean {
        val names = (className() + " " + id()).lowercase()
        if (names.isBlank()) return false
        if (tagName() == "article" || tagName() == "main" || tagName() == "html") return false
        return NEGATIVE.containsMatchIn(names) && !POSITIVE.containsMatchIn(names)
    }

    private fun title(doc: Document): String {
        val raw = meta(doc, "og:title") ?: meta(doc, "twitter:title")
            ?: doc.select("h1").singleOrNull()?.text()
            ?: doc.title()
        val cleaned = raw.trim().replace(Regex("\\s+"), " ")
        // "Chapter 5 - The Return | Site name" → "Chapter 5 - The Return": only a short last part goes.
        val separator = Regex(" [|–—·•] ")
        val last = separator.findAll(cleaned).lastOrNull()
        val kept = if (last != null && cleaned.length - last.range.last <= 40 && last.range.first >= 4) {
            cleaned.substring(0, last.range.first)
        } else {
            cleaned
        }
        return kept.ifBlank { "Untitled page" }
    }

    private fun author(doc: Document): String? {
        val fromMeta = meta(doc, "author") ?: meta(doc, "article:author") ?: meta(doc, "twitter:creator")
        val candidate = fromMeta?.takeUnless { it.startsWith("http") }
            ?: doc.selectFirst("[rel=author], [itemprop=author], .byline, .author")?.text()
        return candidate?.trim()?.removePrefix("By ")?.removePrefix("by ")?.trim()?.takeIf { it.length in 2..80 }
    }

    private fun meta(doc: Document, name: String): String? =
        doc.selectFirst("meta[property=$name], meta[name=$name]")?.attr("content")?.trim()?.takeIf { it.isNotEmpty() }

    private fun host(url: String): String? = runCatching { URI.create(url).host?.removePrefix("www.") }.getOrNull()

    /** An absolute http(s) address, or null for anything else (javascript:, mailto:, broken). */
    fun resolve(base: String, href: String): String? {
        val trimmed = href.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("javascript:", true) ||
            trimmed.startsWith("mailto:", true)
        ) {
            return null
        }
        val resolved = runCatching {
            val baseUrl = java.net.URL(base)
            // java.net drops the last path segment for a bare "?query"; keep it, as browsers do.
            val relative = if (trimmed.startsWith("?")) baseUrl.path + trimmed else trimmed
            java.net.URL(baseUrl, relative.replace(" ", "%20")).toString()
        }.getOrNull() ?: return null
        return resolved.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private const val REMOVE =
        "script, style, noscript, iframe, form, button, input, select, textarea, nav, footer, aside, " +
            "svg, canvas, template, dialog, link, meta, [hidden], [aria-hidden=true], .sr-only"

    private val CONTAINERS = setOf("div", "section", "article", "main", "header", "span")

    private val EMPTY_REMOVABLE = setOf("div", "span", "p", "section", "li", "ul", "ol", "figure")

    private val NEGATIVE = Regex(
        "(^|[\\s_-])(comments?|share|sharing|social|related|sidebar|footer|nav|navbar|menu|breadcrumbs?|" +
            "ad|ads|advert|advertisement|promo|subscribe|newsletter|cookie|popup|modal|banner|sponsored?|" +
            "widget|recommended|outbrain|taboola|signup|paywall|disqus)([\\s_-]|$)",
    )
    private val POSITIVE = Regex("(^|[\\s_-])(article|content|entry|post|story|text|body|chapter|main|reading)([\\s_-]|$)")
    private val NEXT_TEXT = Regex(
        "^(next|next chapter|next page|next part|next ›|next »|next →|›|»|→|>|>>|" +
            "下一章|下一页|下一頁|下一章节|次へ|次の話|다음|다음 화|siguiente|capítulo siguiente|suivant|" +
            "chapitre suivant|próximo|weiter|nächstes kapitel|далее|следующая|следующая глава|tiếp|selanjutnya)" +
            "[\\s>»›→]*$",
    )
    private val NEXT_CLASS = Regex("(^|[\\s_-])(next|nextchap|next-chap|next_chap|btn-next|nav-next)([\\s_-]|$)")
}
