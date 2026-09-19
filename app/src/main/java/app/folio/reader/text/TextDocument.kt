package app.folio.reader.text

import app.folio.core.model.BookFormat
import app.folio.core.model.BookLocation
import app.folio.core.model.TocEntry
import app.folio.data.files.FileAccess
import app.folio.reader.api.BookOpenError
import app.folio.reader.api.BookSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Safelist

data class TextBlock(
    val index: Int,
    val html: String,
    val text: String,
    val headingLevel: Int = 0,
)

/**
 * TXT, Markdown and HTML are all turned into a list of blocks. Blocks give the reader stable
 * anchors: a reading position is a block plus a fraction, and a highlight is a character range
 * inside one block.
 */
class TextDocument(
    val blocks: List<TextBlock>,
    val title: String?,
    val author: String?,
) {
    fun bodyHtml(): String = buildString {
        blocks.forEach { block ->
            append("<div class=\"blk\" data-i=\"").append(block.index).append("\">")
            append(block.html)
            append("</div>")
        }
    }

    fun toc(): List<TocEntry> {
        val headings = blocks.filter { it.headingLevel in 1..3 && it.text.isNotBlank() }
        if (headings.size < 2) return emptyList()
        return headings.map { block ->
            TocEntry(
                title = block.text.take(120),
                location = BookLocation(
                    page = block.index,
                    totalProgression = if (blocks.isEmpty()) 0.0 else block.index.toDouble() / blocks.size,
                    label = block.text.take(40),
                ),
                level = (block.headingLevel - 1).coerceAtLeast(0),
            )
        }
    }

    companion object {
        const val MAX_BYTES = 12L * 1024 * 1024

        suspend fun load(files: FileAccess, source: BookSource): Result<TextDocument> =
            withContext(Dispatchers.IO) {
                val info = files.info(source.uri)
                    ?: return@withContext Result.failure(TextOpenException(BookOpenError.FileMissing))
                if (info.size > MAX_BYTES) {
                    return@withContext Result.failure(
                        TextOpenException(BookOpenError.Failed("file too large")),
                    )
                }
                val raw = try {
                    files.openInput(source.uri)?.use { it.readBytes() }
                } catch (e: Exception) {
                    null
                } ?: return@withContext Result.failure(TextOpenException(BookOpenError.PermissionDenied))

                val content = raw.decodeText()
                try {
                    val document = when (source.format) {
                        BookFormat.MARKDOWN -> fromMarkdown(content)
                        BookFormat.HTML -> fromHtml(content)
                        else -> fromPlainText(content)
                    }
                    if (document.blocks.isEmpty()) {
                        Result.failure(TextOpenException(BookOpenError.Empty))
                    } else {
                        Result.success(document)
                    }
                } catch (e: Exception) {
                    Result.failure(TextOpenException(BookOpenError.Corrupted(e.message)))
                }
            }

        private fun ByteArray.decodeText(): String {
            // Honour a BOM when present, otherwise assume UTF-8 and fall back to Latin-1.
            return when {
                size >= 3 && this[0] == 0xEF.toByte() && this[1] == 0xBB.toByte() && this[2] == 0xBF.toByte() ->
                    String(this, 3, size - 3, Charsets.UTF_8)
                size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xFE.toByte() ->
                    String(this, 2, size - 2, Charsets.UTF_16LE)
                size >= 2 && this[0] == 0xFE.toByte() && this[1] == 0xFF.toByte() ->
                    String(this, 2, size - 2, Charsets.UTF_16BE)
                else -> {
                    val utf8 = String(this, Charsets.UTF_8)
                    if (utf8.contains('�')) String(this, Charsets.ISO_8859_1) else utf8
                }
            }
        }

        fun fromPlainText(content: String): TextDocument {
            val paragraphs = content.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
            val blocks = paragraphs
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapIndexed { index, paragraph ->
                    TextBlock(
                        index = index,
                        html = "<p>${escapeHtml(paragraph).replace("\n", "<br>")}</p>",
                        text = paragraph.replace('\n', ' '),
                    )
                }
            val title = blocks.firstOrNull()?.text?.takeIf { it.length in 1..90 }
            return TextDocument(blocks, title, null)
        }

        fun fromMarkdown(content: String): TextDocument {
            val parser = Parser.builder().build()
            val renderer = HtmlRenderer.builder().build()
            val html = renderer.render(parser.parse(content))
            val document = Jsoup.parseBodyFragment(html)
            val blocks = document.body().children().toBlocks()
            val title = blocks.firstOrNull { it.headingLevel == 1 }?.text
                ?: content.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
            return TextDocument(blocks, title, null)
        }

        fun fromHtml(content: String): TextDocument {
            val parsed: Document = Jsoup.parse(content)
            parsed.select("script, style, iframe, object, embed, noscript, link, meta[http-equiv]").remove()
            val cleanedBody = Jsoup.clean(parsed.body().html(), "", RICH_SAFELIST)
            val document = Jsoup.parseBodyFragment(cleanedBody)
            val blocks = document.body().children().toBlocks()
            val title = parsed.title().takeIf { it.isNotBlank() }
                ?: blocks.firstOrNull { it.headingLevel == 1 }?.text
            val author = parsed.select("meta[name=author]").attr("content").takeIf { it.isNotBlank() }
            return TextDocument(blocks, title, author)
        }

        private fun List<Element>.toBlocks(): List<TextBlock> =
            mapNotNull { element ->
                val text = element.text().trim()
                if (text.isBlank() && element.select("img").isEmpty()) null else element
            }.mapIndexed { index, element ->
                TextBlock(
                    index = index,
                    html = element.outerHtml(),
                    text = element.text().trim(),
                    headingLevel = element.tagName().let { tag ->
                        if (tag.length == 2 && tag[0] == 'h' && tag[1].isDigit()) tag[1].digitToInt() else 0
                    },
                )
            }

        fun escapeHtml(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        private val RICH_SAFELIST: Safelist = Safelist.relaxed()
            .addTags("figure", "figcaption", "section", "article", "hr")
            .addAttributes(":all", "id", "class")
    }
}

class TextOpenException(val error: BookOpenError) : Exception(error.toString())
