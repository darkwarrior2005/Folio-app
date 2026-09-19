package app.folio.reader.comic

import androidx.compose.runtime.Composable
import app.folio.core.model.BookFormat
import app.folio.core.model.BookMetadata
import app.folio.core.model.MetadataResolver
import app.folio.core.model.ReaderFamily
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.reader.api.BookSource
import app.folio.reader.api.ExtractedMetadata
import app.folio.reader.api.MetadataExtractor
import app.folio.reader.api.ReaderEngine
import app.folio.reader.api.ReaderHost
import app.folio.reader.api.TextExtractor

class ComicEngine(
    private val files: FileAccess,
    private val cache: CacheStore,
) : ReaderEngine {
    override val family = ReaderFamily.COMIC
    override val metadataExtractor: MetadataExtractor = ComicMetadataExtractor(files, cache)

    /** Comics carry no searchable text (OCR would be needed). */
    override val textExtractor: TextExtractor? = null

    override fun handles(format: BookFormat): Boolean = format.family == ReaderFamily.COMIC

    @Composable
    override fun Content(host: ReaderHost) {
        ComicReaderContent(host = host, files = files, cache = cache)
    }
}

class ComicMetadataExtractor(
    private val files: FileAccess,
    private val cache: CacheStore,
) : MetadataExtractor {

    override suspend fun extract(source: BookSource): Result<ExtractedMetadata> {
        val opened = ComicSources.open(files, cache, source)
        val comic = opened.getOrElse { return Result.failure(it) }
        return try {
            val info = comic.comicInfo()
            val cover = comic.pageBytes(0)?.let { ComicSources.decode(it, COVER_WIDTH_PX) }
            val fallbackTitle = MetadataResolver.titleFromFileName(source.fileName)
            Result.success(
                ExtractedMetadata(
                    metadata = BookMetadata(
                        title = info?.title ?: info?.series?.let { series ->
                            info.number?.let { number -> "$series ${formatNumber(number)}" } ?: series
                        } ?: fallbackTitle,
                        author = info?.writer,
                        description = info?.summary,
                        series = info?.series,
                        volume = info?.number ?: info?.volume?.toDouble(),
                        year = info?.year,
                        language = info?.language,
                        publisher = info?.publisher,
                    ),
                    pageCount = comic.pageCount,
                    cover = cover,
                    rightToLeft = info?.rightToLeft,
                ),
            )
        } finally {
            comic.close()
        }
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

    companion object {
        const val COVER_WIDTH_PX = 480
    }
}
