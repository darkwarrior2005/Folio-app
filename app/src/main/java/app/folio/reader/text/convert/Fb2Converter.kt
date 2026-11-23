package app.folio.reader.text.convert

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.ZipInputStream

/**
 * FictionBook 2 (.fb2, and .fb2.zip / .fbz holding one). The XML's own encoding declaration is
 * honoured, which matters: many FB2 books are in windows-1251.
 */
object Fb2Converter {

    fun convert(bytes: ByteArray): ConvertedBook {
        val xml = if (bytes.isZip()) unzipFirstFb2(bytes) else bytes
        val doc = runCatching { Jsoup.parse(ByteArrayInputStream(xml), null, "", Parser.xmlParser()) }
            .getOrElse { throw ConversionException(ConversionException.Reason.DAMAGED, it.message) }
        val root = doc.selectFirst("FictionBook") ?: doc.children().firstOrNull()
            ?: throw ConversionException(ConversionException.Reason.DAMAGED, "no FictionBook")

        val binaries = root.select("binary").associate { it.attr("id") to it }
        val images = ImageBudget()
        fun image(href: String): String? {
            val binary = binaries[href.removePrefix("#")] ?: return null
            val data = runCatching { Base64.getMimeDecoder().decode(binary.text()) }.getOrNull()
            return images.dataUri(data, binary.attr("content-type"))
        }

        val info = root.selectFirst("description > title-info")
        val title = info?.selectFirst("book-title")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val author = info?.select("author")?.mapNotNull { a ->
            listOf("first-name", "middle-name", "last-name")
                .mapNotNull { a.selectFirst(it)?.text()?.trim()?.takeIf(String::isNotEmpty) }
                .joinToString(" ")
                .ifBlank { a.selectFirst("nickname")?.text()?.trim() }
                ?.takeIf { it.isNotBlank() }
        }?.joinToString(", ")?.takeIf { it.isNotBlank() }
        val coverHref = info?.selectFirst("coverpage image")?.href()
        val cover = coverHref?.let { binaries[it.removePrefix("#")] }
            ?.let { runCatching { Base64.getMimeDecoder().decode(it.text()) }.getOrNull() }

        val html = StringBuilder()
        root.children().filter { it.tagName() == "body" }.forEach { body ->
            render(body, html, depth = 0, ::image)
        }
        return ConvertedBook(html.toString(), title, author, cover)
    }

    private fun render(node: Node, out: StringBuilder, depth: Int, image: (String) -> String?) {
        when (node) {
            is TextNode -> out.append(escape(node.text()))
            is Element -> when (node.tagName()) {
                "body", "section" -> {
                    node.childNodes().forEach { render(it, out, depth + 1, image) }
                }
                "title" -> {
                    val level = depth.coerceIn(1, 6)
                    val text = node.children().filter { it.tagName() == "p" }.map { it.text().trim() }
                        .ifEmpty { listOf(node.text().trim()) }
                        .filter { it.isNotEmpty() }
                    if (text.isNotEmpty()) out.append("<h$level>").append(escape(text.joinToString(" — "))).append("</h$level>")
                }
                "subtitle" -> wrap("h${(depth + 1).coerceIn(2, 6)}", node, out, depth, image)
                "p" -> wrap("p", node, out, depth, image)
                "v" -> wrap("p", node, out, depth, image)
                "emphasis" -> wrap("em", node, out, depth, image)
                "strong" -> wrap("strong", node, out, depth, image)
                "strikethrough" -> wrap("s", node, out, depth, image)
                "sub", "sup", "code" -> wrap(node.tagName(), node, out, depth, image)
                "epigraph", "cite" -> wrap("blockquote", node, out, depth, image)
                "poem", "stanza" -> wrap("div", node, out, depth, image)
                "text-author" -> wrap("p", node, out, depth, image)
                "empty-line" -> out.append("<p>&nbsp;</p>")
                "a" -> wrap("span", node, out, depth, image)
                "image" -> node.href()?.let(image)?.let { out.append("<p><img src=\"").append(it).append("\"></p>") }
                "table" -> wrap("table", node, out, depth, image)
                "tr" -> wrap("tr", node, out, depth, image)
                "td", "th" -> wrap(node.tagName(), node, out, depth, image)
                else -> node.childNodes().forEach { render(it, out, depth, image) }
            }
        }
    }

    private fun wrap(tag: String, node: Element, out: StringBuilder, depth: Int, image: (String) -> String?) {
        out.append('<').append(tag).append('>')
        node.childNodes().forEach { render(it, out, depth, image) }
        out.append("</").append(tag).append('>')
    }

    /** `l:href`, `xlink:href`, whatever the prefix. */
    private fun Element.href(): String? =
        attributes().firstOrNull { it.key == "href" || it.key.endsWith(":href") }?.value?.takeIf { it.isNotBlank() }

    private fun ByteArray.isZip() = size > 4 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte()

    private fun unzipFirstFb2(bytes: ByteArray): ByteArray {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) return zip.readBytes()
            }
        }
        throw ConversionException(ConversionException.Reason.DAMAGED, "no .fb2 inside the archive")
    }
}
