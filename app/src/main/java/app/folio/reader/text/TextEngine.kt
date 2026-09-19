package app.folio.reader.text

import androidx.compose.runtime.Composable
import app.folio.core.model.BookFormat
import app.folio.core.model.BookLocation
import app.folio.core.model.BookMetadata
import app.folio.core.model.MetadataResolver
import app.folio.core.model.ReaderFamily
import app.folio.data.files.FileAccess
import app.folio.reader.api.BookSource
import app.folio.reader.api.ExtractedMetadata
import app.folio.reader.api.MetadataExtractor
import app.folio.reader.api.ReaderEngine
import app.folio.reader.api.ReaderHost
import app.folio.reader.api.TextChunk
import app.folio.reader.api.TextExtractor

class TextEngine(private val files: FileAccess) : ReaderEngine {
    override val family = ReaderFamily.TEXT
    override val metadataExtractor: MetadataExtractor = TextMetadataExtractor(files)
    override val textExtractor: TextExtractor = PlainTextExtractor(files)

    override fun handles(format: BookFormat): Boolean = format.family == ReaderFamily.TEXT

    @Composable
    override fun Content(host: ReaderHost) {
        TextReaderContent(host = host, files = files)
    }
}

class TextMetadataExtractor(private val files: FileAccess) : MetadataExtractor {

    override suspend fun extract(source: BookSource): Result<ExtractedMetadata> {
        val document = TextDocument.load(files, source).getOrElse { return Result.failure(it) }
        val title = document.title?.trim()?.takeIf { it.isNotBlank() && it.length <= 120 }
            ?: MetadataResolver.titleFromFileName(source.fileName)
        return Result.success(
            ExtractedMetadata(
                metadata = BookMetadata(title = title, author = document.author),
                pageCount = document.blocks.size,
                cover = null,
            ),
        )
    }
}

class PlainTextExtractor(private val files: FileAccess) : TextExtractor {

    override suspend fun extract(source: BookSource, onChunk: suspend (TextChunk) -> Unit) {
        val document = TextDocument.load(files, source).getOrNull() ?: return
        val total = document.blocks.size.coerceAtLeast(1)
        val buffer = StringBuilder()
        var startIndex = 0
        var label = ""

        suspend fun flush() {
            if (buffer.isNotBlank()) {
                onChunk(
                    TextChunk(
                        location = BookLocation(
                            page = startIndex,
                            totalProgression = startIndex.toDouble() / total,
                            label = label,
                        ),
                        label = label,
                        text = buffer.toString().trim(),
                    ),
                )
            }
            buffer.setLength(0)
        }

        document.blocks.forEach { block ->
            if (buffer.isEmpty()) {
                startIndex = block.index
                if (block.headingLevel > 0) label = block.text.take(60)
            }
            buffer.append(block.text).append('\n')
            if (buffer.length >= CHUNK_CHARS) flush()
        }
        flush()
    }

    companion object {
        const val CHUNK_CHARS = 1500
    }
}
