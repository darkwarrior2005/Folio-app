package app.folio.reader.text.convert

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** The parts of a zip-based document, by path. Pictures and XML only; nothing is unpacked to disk. */
internal class ZipParts(bytes: ByteArray, keep: (String) -> Boolean) {
    private val parts = HashMap<String, ByteArray>()

    init {
        runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && keep(entry.name)) parts[entry.name] = zip.readBytes()
                }
            }
        }.onFailure { throw ConversionException(ConversionException.Reason.DAMAGED, it.message) }
    }

    operator fun get(path: String): ByteArray? = parts[path]

    fun xml(path: String): Document? = parts[path]?.let { Jsoup.parse(ByteArrayInputStream(it), "UTF-8", "", Parser.xmlParser()) }
}

/** Child elements by their qualified XML name ("w:p"), which jsoup's CSS selectors make awkward. */
internal fun Element.kids(name: String): List<Element> = children().filter { it.tagName() == name }

internal fun Element.kid(name: String): Element? = children().firstOrNull { it.tagName() == name }

internal fun Element.descendants(name: String): List<Element> = getAllElements().filter { it.tagName() == name }

/** Word documents (.docx): headings, paragraphs, bold/italic/underline, lists, tables, pictures. */
object DocxConverter {

    fun convert(bytes: ByteArray): ConvertedBook {
        val zip = ZipParts(bytes) { it.startsWith("word/") || it.startsWith("docProps/") }
        val document = zip.xml("word/document.xml")
            ?: throw ConversionException(ConversionException.Reason.DAMAGED, "no word/document.xml")
        val body = document.descendants("w:body").firstOrNull()
            ?: throw ConversionException(ConversionException.Reason.DAMAGED, "no body")

        val headingStyles = headingLevels(zip.xml("word/styles.xml"))
        val relations = zip.xml("word/_rels/document.xml.rels")?.descendants("Relationship")
            ?.associate { it.attr("Id") to it.attr("Target") }.orEmpty()
        val images = ImageBudget()
        fun picture(id: String): String? {
            val target = relations[id] ?: return null
            val path = if (target.startsWith("/")) target.removePrefix("/") else "word/" + target.removePrefix("./")
            return images.dataUri(zip[path])
        }

        val html = StringBuilder()
        var inList = false
        fun closeList() {
            if (inList) html.append("</ul>")
            inList = false
        }
        body.children().forEach { block ->
            when (block.tagName()) {
                "w:p" -> {
                    val props = block.kid("w:pPr")
                    val style = props?.kid("w:pStyle")?.attr("w:val")
                    val level = style?.let { headingStyles[it] ?: builtInHeading(it) }
                        ?: props?.kid("w:outlineLvl")?.attr("w:val")?.toIntOrNull()?.plus(1)
                    val listed = props?.kid("w:numPr") != null || style?.startsWith("List", ignoreCase = true) == true
                    val content = runs(block, ::picture)
                    if (content.isBlank()) {
                        closeList()
                        return@forEach
                    }
                    when {
                        level != null && level in 1..6 -> {
                            closeList()
                            html.append("<h$level>").append(content).append("</h$level>")
                        }
                        listed -> {
                            if (!inList) html.append("<ul>")
                            inList = true
                            html.append("<li>").append(content).append("</li>")
                        }
                        else -> {
                            closeList()
                            html.append("<p>").append(content).append("</p>")
                        }
                    }
                }
                "w:tbl" -> {
                    closeList()
                    html.append("<table>")
                    block.kids("w:tr").forEach { row ->
                        html.append("<tr>")
                        row.kids("w:tc").forEach { cell ->
                            html.append("<td>")
                            cell.kids("w:p").map { runs(it, ::picture) }.filter { it.isNotBlank() }
                                .joinTo(html, "<br>")
                            html.append("</td>")
                        }
                        html.append("</tr>")
                    }
                    html.append("</table>")
                }
                "w:sdt" -> {
                    // Content controls (a generated table of contents, for one) wrap ordinary paragraphs.
                    block.kid("w:sdtContent")?.kids("w:p")?.forEach { p ->
                        runs(p, ::picture).takeIf { it.isNotBlank() }?.let { html.append("<p>").append(it).append("</p>") }
                    }
                }
            }
        }
        closeList()

        val core = zip.xml("docProps/core.xml")
        val title = core?.descendants("dc:title")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val author = core?.descendants("dc:creator")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() }
        return ConvertedBook(html.toString(), title, author, null)
    }

    /** The text of a paragraph's runs, with their emphasis, links' text and pictures. */
    private fun runs(paragraph: Element, picture: (String) -> String?): String {
        val out = StringBuilder()
        fun run(r: Element) {
            val props = r.kid("w:rPr")
            fun on(name: String) = props?.kid(name)?.let { it.attr("w:val").let { v -> v.isEmpty() || v == "true" || v == "1" } } == true
            val bold = on("w:b")
            val italic = on("w:i")
            val underline = props?.kid("w:u")?.attr("w:val")?.let { it.isNotEmpty() && it != "none" } == true
            val text = StringBuilder()
            r.children().forEach { part ->
                when (part.tagName()) {
                    "w:t" -> text.append(escape(part.wholeText()))
                    "w:tab" -> text.append(' ')
                    "w:br", "w:cr" -> text.append("<br>")
                    "w:drawing", "w:pict" -> part.getAllElements()
                        .firstNotNullOfOrNull { e -> e.attributes().firstOrNull { it.key == "r:embed" || it.key == "r:id" }?.value }
                        ?.let(picture)?.let { text.append("<img src=\"").append(it).append("\">") }
                }
            }
            if (text.isEmpty()) return
            if (bold) out.append("<strong>")
            if (italic) out.append("<em>")
            if (underline) out.append("<u>")
            out.append(text)
            if (underline) out.append("</u>")
            if (italic) out.append("</em>")
            if (bold) out.append("</strong>")
        }
        fun walk(e: Element) {
            e.children().forEach { child ->
                when (child.tagName()) {
                    "w:r" -> run(child)
                    "w:hyperlink", "w:smartTag", "w:ins", "w:fldSimple", "w:sdt", "w:sdtContent" -> walk(child)
                }
            }
        }
        walk(paragraph)
        return out.toString()
    }

    /** Style ids whose name is "heading N" or "Title", which is how Word marks headings in any language. */
    private fun headingLevels(styles: Document?): Map<String, Int> {
        styles ?: return emptyMap()
        return styles.descendants("w:style").mapNotNull { style ->
            val id = style.attr("w:styleId").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = style.kid("w:name")?.attr("w:val")?.lowercase().orEmpty()
            val outline = style.kid("w:pPr")?.kid("w:outlineLvl")?.attr("w:val")?.toIntOrNull()
            val level = when {
                name == "title" -> 1
                name.startsWith("heading ") -> name.removePrefix("heading ").toIntOrNull()
                outline != null && outline < 6 -> outline + 1
                else -> null
            }
            level?.let { id to it }
        }.toMap()
    }

    private fun builtInHeading(style: String): Int? = when {
        style.equals("Title", ignoreCase = true) -> 1
        style.startsWith("Heading", ignoreCase = true) -> style.drop(7).toIntOrNull()
        else -> null
    }
}

/** OpenDocument text (.odt): headings, paragraphs, lists, tables, emphasis from styles, pictures. */
object OdtConverter {

    fun convert(bytes: ByteArray): ConvertedBook {
        val zip = ZipParts(bytes) { it.endsWith(".xml") || it.startsWith("Pictures/") }
        val content = zip.xml("content.xml") ?: throw ConversionException(ConversionException.Reason.DAMAGED, "no content.xml")
        val text = content.descendants("office:text").firstOrNull()
            ?: throw ConversionException(ConversionException.Reason.DAMAGED, "no text body")

        // Automatic styles say which spans are bold or italic.
        val emphasis = HashMap<String, Pair<Boolean, Boolean>>()
        (content.descendants("style:style") + (zip.xml("styles.xml")?.descendants("style:style").orEmpty())).forEach { style ->
            val props = style.kid("style:text-properties") ?: return@forEach
            val bold = props.attr("fo:font-weight").let { it == "bold" || (it.toIntOrNull() ?: 0) >= 600 }
            val italic = props.attr("fo:font-style") == "italic"
            if (bold || italic) emphasis[style.attr("style:name")] = bold to italic
        }
        val images = ImageBudget()

        fun inline(node: Node, out: StringBuilder) {
            when (node) {
                is TextNode -> out.append(escape(node.wholeText))
                is Element -> when (node.tagName()) {
                    "text:s" -> out.append(" ".repeat(node.attr("text:c").toIntOrNull()?.coerceIn(1, 8) ?: 1))
                    "text:tab" -> out.append(' ')
                    "text:line-break" -> out.append("<br>")
                    "text:note" -> Unit // Footnotes would interrupt the sentence; they are left out.
                    "draw:frame" -> node.descendants("draw:image").firstOrNull()
                        ?.attributes()?.firstOrNull { it.key == "xlink:href" }?.value
                        ?.let { images.dataUri(zip[it.removePrefix("./")]) }
                        ?.let { out.append("<img src=\"").append(it).append("\">") }
                    "text:span" -> {
                        val (bold, italic) = emphasis[node.attr("text:style-name")] ?: (false to false)
                        if (bold) out.append("<strong>")
                        if (italic) out.append("<em>")
                        node.childNodes().forEach { inline(it, out) }
                        if (italic) out.append("</em>")
                        if (bold) out.append("</strong>")
                    }
                    else -> node.childNodes().forEach { inline(it, out) }
                }
            }
        }

        val html = StringBuilder()
        fun block(e: Element) {
            when (e.tagName()) {
                "text:h" -> {
                    val level = e.attr("text:outline-level").toIntOrNull()?.coerceIn(1, 6) ?: 2
                    html.append("<h$level>")
                    e.childNodes().forEach { inline(it, html) }
                    html.append("</h$level>")
                }
                "text:p" -> {
                    val line = StringBuilder()
                    e.childNodes().forEach { inline(it, line) }
                    if (line.isNotBlank()) html.append("<p>").append(line).append("</p>")
                }
                "text:list" -> {
                    html.append("<ul>")
                    e.kids("text:list-item").forEach { item ->
                        html.append("<li>")
                        item.children().forEach { child ->
                            if (child.tagName() == "text:list") block(child) else child.childNodes().forEach { inline(it, html) }
                        }
                        html.append("</li>")
                    }
                    html.append("</ul>")
                }
                "table:table" -> {
                    html.append("<table>")
                    e.descendants("table:table-row").forEach { row ->
                        html.append("<tr>")
                        row.kids("table:table-cell").forEach { cell ->
                            html.append("<td>")
                            cell.children().forEach { p -> p.childNodes().forEach { inline(it, html) }; html.append(' ') }
                            html.append("</td>")
                        }
                        html.append("</tr>")
                    }
                    html.append("</table>")
                }
                "text:section", "text:index-body", "text:table-of-content" -> e.children().forEach(::block)
            }
        }
        text.children().forEach(::block)

        val meta = zip.xml("meta.xml")
        val title = meta?.descendants("dc:title")?.firstOrNull()?.text()?.trim()?.takeIf { it.isNotEmpty() }
        val author = (meta?.descendants("dc:creator")?.firstOrNull() ?: meta?.descendants("meta:initial-creator")?.firstOrNull())
            ?.text()?.trim()?.takeIf { it.isNotEmpty() }
        return ConvertedBook(html.toString(), title, author, null)
    }
}
