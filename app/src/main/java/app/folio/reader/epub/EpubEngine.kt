package app.folio.reader.epub

import androidx.compose.runtime.Composable
import app.folio.core.model.BookFormat
import app.folio.core.model.BookMetadata
import app.folio.core.model.ReaderFamily
import app.folio.reader.api.BookSource
import app.folio.reader.api.ExtractedMetadata
import app.folio.reader.api.MetadataExtractor
import app.folio.reader.api.ReaderEngine
import app.folio.reader.api.ReaderHost
import app.folio.reader.api.TextChunk
import app.folio.reader.api.TextExtractor
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.content
import org.readium.r2.shared.publication.services.cover

class EpubEngine(private val readium: ReadiumStack) : ReaderEngine {
    override val family = ReaderFamily.EPUB
    override val metadataExtractor: MetadataExtractor = EpubMetadataExtractor(readium)
    override val textExtractor: TextExtractor = EpubTextExtractor(readium)

    override fun handles(format: BookFormat): Boolean = format == BookFormat.EPUB

    @Composable
    override fun Content(host: ReaderHost) {
        EpubReaderContent(host = host, readium = readium)
    }
}

class EpubMetadataExtractor(private val readium: ReadiumStack) : MetadataExtractor {

    override suspend fun extract(source: BookSource): Result<ExtractedMetadata> {
        val publication = readium.open(source.uri).getOrElse { return Result.failure(it) }
        return try {
            val metadata = publication.metadata
            val series = metadata.belongsToSeries.firstOrNull()
            Result.success(
                ExtractedMetadata(
                    metadata = BookMetadata(
                        title = metadata.title?.trim()?.takeIf { it.isNotBlank() },
                        author = metadata.primaryAuthor(),
                        description = metadata.description?.trim()?.takeIf { it.isNotBlank() },
                        series = series?.name,
                        volume = series?.position,
                        year = metadata.publicationYear(),
                        language = metadata.languages.firstOrNull(),
                        publisher = metadata.publishers.firstOrNull()?.name,
                    ),
                    pageCount = metadata.numberOfPages,
                    cover = publication.cover(),
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            publication.close()
        }
    }
}

internal fun Metadata.primaryAuthor(): String? {
    val names = (authors.takeIf { it.isNotEmpty() } ?: contributors).map { it.name.trim() }.filter { it.isNotBlank() }
    return when {
        names.isEmpty() -> null
        names.size <= 2 -> names.joinToString(" & ")
        else -> "${names.first()} and ${names.size - 1} others"
    }
}

/** Published dates arrive as ISO instants; only the year is shown in the library. */
internal fun Metadata.publicationYear(): Int? {
    val raw = (published ?: modified)?.toString() ?: return null
    return Regex("^(\\d{4})").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1000..2200 }
}

class EpubTextExtractor(private val readium: ReadiumStack) : TextExtractor {

    override suspend fun extract(source: BookSource, onChunk: suspend (TextChunk) -> Unit) {
        val publication = readium.open(source.uri).getOrElse { return }
        try {
            val content = publication.content() ?: return
            val iterator = content.iterator()
            val buffer = StringBuilder()
            var chunkStart: app.folio.core.model.BookLocation? = null
            var label = ""

            suspend fun flush() {
                val start = chunkStart
                if (start != null && buffer.isNotBlank()) {
                    onChunk(TextChunk(start, label, buffer.toString().trim()))
                }
                buffer.setLength(0)
                chunkStart = null
            }

            while (true) {
                val element = iterator.nextOrNull() ?: break
                if (element !is Content.TextElement) continue
                val text = element.text.trim()
                if (text.isBlank()) continue
                if (chunkStart == null) {
                    chunkStart = element.locator.toBookLocation()
                    label = element.locator.title?.takeIf { it.isNotBlank() } ?: label
                }
                buffer.append(text).append('\n')
                if (buffer.length >= CHUNK_CHARS) flush()
            }
            flush()
        } catch (e: Exception) {
            // A malformed chapter should not abort indexing the rest of the library.
        } finally {
            publication.close()
        }
    }

    companion object {
        const val CHUNK_CHARS = 1500
    }
}
