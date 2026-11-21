package app.folio.reader.text.convert

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.util.zip.ZipInputStream

/** FictionBook 2: body/section/title become headings, binary pictures are embedded as data URIs. */
object Fb2Converter {

    fun convert(bytes: ByteArray, isZipped: Boolean): ConvertedBook {
        val xmlBytes = if (isZipped) unzipSingleEntry(bytes) else bytes
        // Jsoup's XML parser honours the <?xml encoding="..."?> declaration (often windows-1251).
        val document = Jsoup.parse(xmlBytes.inputStream(), null, "", Parser.xmlParser())

        val images = document.select("binary").associate { bin ->
            bin.attr("id") to Pair(bin.attr("content-type").ifBlank { "image/jpeg" }, bin.text())
        }
        val budget = ImageBudget()
        val imageUrls = images.mapValues { (_, value) ->
            val (mime, base64) = value
            val decoded = try { java.util.Base64.getMimeDecoder().decode(base64) } catch (e: Exception) { null }
            decoded?.let { budget.tryAdd(it, mime) }
        }

        val title = document.select("title-info > book-title").firstOrNull()?.text()
        val author = document.select("title-info > author").firstOrNull()?.let { authorEl ->
            listOfNotNull(authorEl.select("first-name").text(), authorEl.select("last-name").text())
                .filter { it.isNotBlank() }.joinToString(" ")
        }?.takeIf { it.isNotBlank() }

        val html = StringBuilder()
        document.select("body").forEach { body -> renderSection(body, html, imageUrls, depth = 1) }

        val coverId = document.select("coverpage image").firstOrNull()
            ?.let { it.attr("l:href").ifBlank { it.attr("xlink:href") } }
            ?.removePrefix("#")
        val coverBytes = coverId?.let { id -> images[id]?.second?.let { b64 -> runCatching { java.util.Base64.getMimeDecoder().decode(b64) }.getOrNull() } }

        return ConvertedBook(html = html.toString(), title = title, author = author, cover = coverBytes)
    }

    private fun renderSection(element: Element, out: StringBuilder, imageUrls: Map<String, String?>, depth: Int) {
        for (child in element.children()) {
            when (child.tagName()) {
                "title" -> out.append("<h${depth.coerceIn(1, 6)}>").append(child.text()).append("</h${depth.coerceIn(1, 6)}>")
                "section" -> renderSection(child, out, imageUrls, depth + 1)
                "epigraph", "cite" -> out.append("<blockquote>").also { renderSection(child, out, imageUrls, depth) }.append("</blockquote>")
                "p", "subtitle" -> out.append("<p>").append(child.html()).append("</p>")
                "empty-line" -> out.append("<br>")
                "image" -> {
                    val href = (child.attr("l:href").ifBlank { child.attr("xlink:href") }).removePrefix("#")
                    imageUrls[href]?.let { url -> out.append("<img src=\"").append(url).append("\">") }
                }
                else -> if (child.children().isNotEmpty()) renderSection(child, out, imageUrls, depth)
            }
        }
    }

    private fun unzipSingleEntry(bytes: ByteArray): ByteArray {
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".fb2", ignoreCase = true)) {
                    return zip.readBytes()
                }
                entry = zip.nextEntry
            }
        }
        throw ConversionException(ConversionReason.DAMAGED, "No .fb2 entry found in archive")
    }
}
