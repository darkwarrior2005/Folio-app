package app.folio.reader

import app.folio.core.model.BookFormat
import app.folio.reader.api.MetadataExtractor
import app.folio.reader.api.ReaderEngine
import app.folio.reader.api.TextExtractor

/**
 * The one place that knows which reader handles which format. Supporting a new format means
 * adding an engine here; nothing else in the app changes.
 */
class ReaderRegistry(private val engines: List<ReaderEngine>) {

    fun engineFor(format: BookFormat): ReaderEngine? = engines.firstOrNull { it.handles(format) }

    fun metadataExtractor(format: BookFormat): MetadataExtractor? = engineFor(format)?.metadataExtractor

    fun textExtractor(format: BookFormat): TextExtractor? = engineFor(format)?.textExtractor

    fun supports(format: BookFormat): Boolean = engineFor(format) != null
}
