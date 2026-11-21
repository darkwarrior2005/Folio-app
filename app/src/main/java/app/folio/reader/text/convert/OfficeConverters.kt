package app.folio.reader.text.convert

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.zip.ZipInputStream

/**
 * Reads a zip-based office document's parts into memory, keyed by their path inside the archive.
 */
class ZipParts private constructor(private val entries: Map<String, ByteArray>) {

    fun text(path: String): String? = entries[path]?.decodeToString()
    fun bytes(path: String): ByteArray? = entries[path]
    fun exists(path: String): Boolean = path in entries

    companion object {
        fun read(bytes: ByteArray): ZipParts {
            val map = mutableMapOf<String, ByteArray>()
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) map[entry.name] = zip.readBytes()
                    entry = zip.nextEntry
                }
            }
            return ZipParts(map)
        }
    }
}

/**
 * Jsoup's XML parser treats a namespaced tag like `w:p` as the literal tag name "w:p"; CSS
 * selectors need the colon escaped (`w\:p`) since a bare colon starts a pseudo-selector.
 */
private fun Element.selectNs(tag: String): org.jsoup.select.Elements = select(tag.replace(":", "\\:"))
private fun Element.firstNs(tag: String): Element? = selectNs(tag).firstOrNull()
private fun Element.attrNs(tag: String): String = attr(tag)

object DocxConverter {

    fun convert(bytes: ByteArray): ConvertedBook {
        val zip = ZipParts.read(bytes)
        val documentXml = zip.text("word/document.xml")
            ?: throw ConversionException(ConversionReason.DAMAGED, "Missing word/document.xml")
        val document = Jsoup.parse(documentXml, "", Parser.xmlParser())

        val headingStyleIds = headingStyleIds(zip.text("word/styles.xml"))
        val relationships = parseRelationships(zip.text("word/_rels/document.xml.rels"))
        val budget = ImageBudget()

        val body = document.firstNs("w:body") ?: document
        val html = StringBuilder()
        body.children().forEach { element ->
            when (element.tagName()) {
                "w:p" -> html.append(renderParagraph(element, headingStyleIds, relationships, zip, budget))
                "w:tbl" -> html.append(renderTable(element, relationships, zip, budget))
            }
        }

        val core = zip.text("docProps/core.xml")?.let { Jsoup.parse(it, "", Parser.xmlParser()) }
        val title = core?.firstNs("dc:title")?.text()?.takeIf { it.isNotBlank() }
        val author = core?.firstNs("dc:creator")?.text()?.takeIf { it.isNotBlank() }

        return ConvertedBook(html.toString(), title, author)
    }

    private fun headingStyleIds(stylesXml: String?): Map<String, Int> {
        if (stylesXml == null) return emptyMap()
        val styles = Jsoup.parse(stylesXml, "", Parser.xmlParser())
        val result = mutableMapOf<String, Int>()
        styles.selectNs("w:style").forEach { style ->
            val id = style.attrNs("w:styleId")
            val name = style.firstNs("w:name")?.attrNs("w:val").orEmpty()
            val level = Regex("heading ?([1-6])", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.toIntOrNull()
            if (level != null && id.isNotBlank()) result[id] = level
        }
        return result
    }

    private fun renderParagraph(
        p: Element,
        headingStyleIds: Map<String, Int>,
        relationships: Map<String, String>,
        zip: ZipParts,
        budget: ImageBudget,
    ): String {
        val pPr = p.firstNs("w:pPr")
        val styleId = pPr?.firstNs("w:pStyle")?.attrNs("w:val").orEmpty()
        val outlineLvl = pPr?.firstNs("w:outlineLvl")?.attrNs("w:val")?.toIntOrNull()
        val level = headingStyleIds[styleId] ?: outlineLvl?.plus(1)
        val isListItem = pPr?.firstNs("w:numPr") != null

        val runsHtml = p.selectNs("w:r").joinToString("") { run ->
            val text = run.selectNs("w:t").joinToString("") { it.text() }
            val drawing = run.selectNs("a:blip").firstOrNull() ?: run.selectNs("v:imagedata").firstOrNull()
            val imageHtml = drawing?.let { renderEmbeddedImage(it, relationships, zip, budget) }.orEmpty()
            if (text.isBlank() && imageHtml.isBlank()) return@joinToString ""
            var runHtml = escape(text)
            val rPr = run.firstNs("w:rPr")
            if (rPr?.firstNs("w:b") != null) runHtml = "<b>$runHtml</b>"
            if (rPr?.firstNs("w:i") != null) runHtml = "<i>$runHtml</i>"
            if (rPr?.firstNs("w:u") != null) runHtml = "<u>$runHtml</u>"
            runHtml + imageHtml
        }
        if (runsHtml.isBlank()) return ""

        return when {
            level != null -> "<h${level.coerceIn(1, 6)}>$runsHtml</h${level.coerceIn(1, 6)}>"
            isListItem -> "<li>$runsHtml</li>"
            else -> "<p>$runsHtml</p>"
        }
    }

    private fun renderEmbeddedImage(
        blip: Element,
        relationships: Map<String, String>,
        zip: ZipParts,
        budget: ImageBudget,
    ): String {
        val relId = blip.attrNs("r:embed").ifBlank { blip.attrNs("r:id") }
        val target = relationships[relId] ?: return ""
        val path = "word/" + target.removePrefix("/")
        val bytes = zip.bytes(path) ?: return ""
        val url = budget.tryAdd(bytes, guessImageMimeType(bytes)) ?: return ""
        return "<img src=\"$url\">"
    }

    private fun renderTable(
        table: Element,
        relationships: Map<String, String>,
        zip: ZipParts,
        budget: ImageBudget,
    ): String {
        val rows = table.selectNs("w:tr").joinToString("") { row ->
            val cells = row.selectNs("w:tc").joinToString("") { cell ->
                "<td>" + cell.selectNs("w:p").joinToString("") { renderParagraph(it, emptyMap(), relationships, zip, budget) } + "</td>"
            }
            "<tr>$cells</tr>"
        }
        return "<table>$rows</table>"
    }

    private fun parseRelationships(relsXml: String?): Map<String, String> {
        if (relsXml == null) return emptyMap()
        val rels = Jsoup.parse(relsXml, "", Parser.xmlParser())
        return rels.select("Relationship").associate { it.attr("Id") to it.attr("Target") }
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

object OdtConverter {

    fun convert(bytes: ByteArray): ConvertedBook {
        val zip = ZipParts.read(bytes)
        val contentXml = zip.text("content.xml") ?: throw ConversionException(ConversionReason.DAMAGED, "Missing content.xml")
        val document = Jsoup.parse(contentXml, "", Parser.xmlParser())

        val styles = document.selectNs("style:style").associate { style ->
            val props = style.firstNs("style:text-properties")
            style.attrNs("style:name") to (props?.attrNs("fo:font-weight").orEmpty() to props?.attrNs("fo:font-style").orEmpty())
        }
        val budget = ImageBudget()

        val html = StringBuilder()
        document.firstNs("office:body")?.firstNs("office:text")?.children()?.forEach { element ->
            html.append(renderNode(element, styles, zip, budget))
        }

        val metaXml = zip.text("meta.xml")?.let { Jsoup.parse(it, "", Parser.xmlParser()) }
        val title = metaXml?.firstNs("dc:title")?.text()?.takeIf { it.isNotBlank() }
        val author = metaXml?.firstNs("meta:initial-creator")?.text()?.takeIf { it.isNotBlank() }
            ?: metaXml?.firstNs("dc:creator")?.text()?.takeIf { it.isNotBlank() }

        return ConvertedBook(html.toString(), title, author)
    }

    private fun renderNode(
        element: Element,
        styles: Map<String, Pair<String, String>>,
        zip: ZipParts,
        budget: ImageBudget,
    ): String = when (element.tagName()) {
        "text:h" -> {
            val level = element.attrNs("text:outline-level").toIntOrNull()?.coerceIn(1, 6) ?: 1
            "<h$level>${renderInline(element, styles)}</h$level>"
        }
        "text:p" -> "<p>${renderInline(element, styles)}${renderImages(element, zip, budget)}</p>"
        "text:list" -> "<ul>" + element.selectNs("text:list-item").joinToString("") { item ->
            "<li>" + item.children().joinToString("") { renderNode(it, styles, zip, budget) } + "</li>"
        } + "</ul>"
        "table:table" -> {
            val rows = element.selectNs("table:table-row").joinToString("") { row ->
                "<tr>" + row.selectNs("table:table-cell").joinToString("") { cell ->
                    "<td>" + cell.children().joinToString("") { renderNode(it, styles, zip, budget) } + "</td>"
                } + "</tr>"
            }
            "<table>$rows</table>"
        }
        else -> ""
    }

    private fun renderInline(element: Element, styles: Map<String, Pair<String, String>>): String {
        val text = escape(element.text())
        val styleName = element.attrNs("text:style-name")
        val (weight, fontStyle) = styles[styleName] ?: ("" to "")
        var html = text
        if (weight == "bold") html = "<b>$html</b>"
        if (fontStyle == "italic") html = "<i>$html</i>"
        return html
    }

    private fun renderImages(element: Element, zip: ZipParts, budget: ImageBudget): String =
        element.selectNs("draw:frame").mapNotNull { it.firstNs("draw:image") }.joinToString("") { image ->
            val href = image.attrNs("xlink:href")
            val bytes = zip.bytes(href) ?: return@joinToString ""
            val url = budget.tryAdd(bytes, guessImageMimeType(bytes)) ?: return@joinToString ""
            "<img src=\"$url\">"
        }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
