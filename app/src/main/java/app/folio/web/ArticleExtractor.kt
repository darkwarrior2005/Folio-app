package app.folio.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

data class ExtractedArticle(
    val title: String?,
    val author: String?,
    val html: String,
    val nextUrl: String?,
)

/**
 * Finds the article in a page the way reader views do: strip the chrome, score what's left for
 * how much it reads like body text, keep the highest-scoring subtree. Pure Jsoup, no Android/
 * JVM-only dependency, so it can run against anything already fetched as a String.
 */
object ArticleExtractor {

    private val UNWANTED_SELECTOR = "script, style, nav, footer, aside, form, noscript, iframe, " +
        "[hidden], [aria-hidden=true], [style*=display:none], [style*=display: none]"
    private val UNWANTED_CLASS_ID = Regex(
        "comment|share|social|related|sidebar|ad|advert|promo|cookie|subscribe|newsletter|popup|banner|breadcrumb",
        RegexOption.IGNORE_CASE,
    )

    private val NEXT_WORDS = setOf(
        "next", "siguiente", "suivant", "weiter", "nächste", "próximo", "próxima",
        "successivo", "次の", "下一頁", "下一章", "далее",
    )

    fun extract(html: String, baseUrl: String): ExtractedArticle {
        val document = Jsoup.parse(html, baseUrl)
        document.select(UNWANTED_SELECTOR).remove()
        removeNoise(document)

        val candidates = document.select("p, div, section, article")
        val scores = mutableMapOf<Element, Double>()
        candidates.forEach { element ->
            val text = element.ownText()
            if (text.length < 25) return@forEach
            val score = 1.0 + text.count { it == ',' } + (text.length / 100.0)
            val classWeight = classWeight(element)
            element.parent()?.let { parent ->
                scores[parent] = (scores[parent] ?: 0.0) + score + classWeight
                parent.parent()?.let { grandparent ->
                    scores[grandparent] = (scores[grandparent] ?: 0.0) + score / 2
                }
            }
        }
        scores.keys.forEach { element ->
            scores[element] = (scores[element] ?: 0.0) / linkDensity(element).coerceAtLeast(0.15)
        }
        document.select("article, main, [role=main]").forEach { element ->
            scores[element] = (scores[element] ?: 0.0) + 25.0
        }

        val best = scores.maxByOrNull { it.value }?.key ?: document.body()
        keepSiblingsAboveThreshold(best, scores)
        resolveLazyImages(best, baseUrl)

        val title = document.title().ifBlank { null }
        val author = document.select("meta[name=author]").attr("content").ifBlank { null }
        val nextUrl = nextLink(document, baseUrl)

        return ExtractedArticle(title, author, flattenToParagraphs(best), nextUrl)
    }

    private fun removeNoise(document: Document) {
        document.select("[class], [id]").forEach { element ->
            val marker = "${element.className()} ${element.id()}"
            if (UNWANTED_CLASS_ID.containsMatchIn(marker) && element.ownText().length < 200) {
                element.remove()
            }
        }
    }

    private fun classWeight(element: Element): Double {
        val marker = "${element.className()} ${element.id()}"
        var weight = 0.0
        if (Regex("article|content|main|body|post|story", RegexOption.IGNORE_CASE).containsMatchIn(marker)) weight += 25.0
        if (UNWANTED_CLASS_ID.containsMatchIn(marker)) weight -= 25.0
        return weight
    }

    private fun linkDensity(element: Element): Double {
        val text = element.text()
        if (text.isEmpty()) return 1.0
        val linkText = element.select("a").joinToString(" ") { it.text() }
        return 1.0 - (linkText.length.toDouble() / text.length).coerceIn(0.0, 0.95)
    }

    private fun keepSiblingsAboveThreshold(best: Element, scores: Map<Element, Double>) {
        val bestScore = scores[best] ?: return
        val parent = best.parent() ?: return
        parent.children().toList().forEach { sibling ->
            if (sibling != best) {
                val siblingScore = scores[sibling] ?: 0.0
                if (siblingScore < bestScore * 0.2) sibling.remove()
            }
        }
    }

    private fun resolveLazyImages(root: Element, baseUrl: String) {
        root.select("img").forEach { img ->
            val lazy = listOf("data-src", "data-lazy-src", "data-original").firstNotNullOfOrNull { attr ->
                img.attr(attr).ifBlank { null }
            } ?: img.attr("srcset").split(",").firstOrNull()?.trim()?.split(" ")?.firstOrNull()
            if (!lazy.isNullOrBlank()) img.attr("src", resolve(baseUrl, lazy))
            val src = img.attr("src")
            if (src.isBlank()) {
                img.remove()
            } else {
                img.attr("src", resolve(baseUrl, src))
                // 1x1 tracking pixels: drop anything explicitly sized that small.
                val w = img.attr("width").toIntOrNull()
                val h = img.attr("height").toIntOrNull()
                if (w != null && h != null && w <= 1 && h <= 1) img.remove()
            }
        }
    }

    private fun flattenToParagraphs(root: Element): String {
        val out = StringBuilder()
        root.children().forEach { child -> appendFlattened(child, out) }
        if (out.isEmpty()) out.append(root.outerHtml())
        return out.toString()
    }

    private fun appendFlattened(element: Element, out: StringBuilder) {
        when (element.tagName()) {
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "figure", "ul", "ol", "img" ->
                out.append(element.outerHtml())
            else -> if (element.children().isEmpty() && element.text().isNotBlank()) {
                out.append("<p>").append(element.html()).append("</p>")
            } else {
                element.children().forEach { appendFlattened(it, out) }
            }
        }
    }

    fun nextLink(document: Document, baseUrl: String): String? {
        document.select("a[rel=next]").firstOrNull()?.attr("href")?.let { return resolve(baseUrl, it) }
        val sameSiteHost = runCatching { URI(baseUrl).host }.getOrNull()
        return document.select("a").firstOrNull { anchor ->
            val text = anchor.text().trim().lowercase()
            val classIdText = "${anchor.className()} ${anchor.id()}".lowercase()
            val looksNext = NEXT_WORDS.any { it in text } || "next" in classIdText
            val href = anchor.attr("href")
            looksNext && href.isNotBlank() && runCatching { URI(resolve(baseUrl, href)).host == sameSiteHost }.getOrElse { false }
        }?.let { resolve(baseUrl, it.attr("href")) }
    }

    /** Fixes Java's URI resolution for a bare `?query` link, which [URI.resolve] mishandles. */
    fun resolve(baseUrl: String, href: String): String {
        if (href.startsWith("?")) {
            val base = baseUrl.substringBefore('?')
            return base + href
        }
        return try {
            URI(baseUrl).resolve(href).toString()
        } catch (e: Exception) {
            href
        }
    }
}
