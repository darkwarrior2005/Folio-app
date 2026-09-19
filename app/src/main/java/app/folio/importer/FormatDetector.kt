package app.folio.importer

import app.folio.core.model.BookFormat
import app.folio.data.files.FileAccess

/**
 * Decides which reader a file belongs to. Extension first, then the declared MIME type, then the
 * file's own magic bytes, so a mislabelled ".txt" that is really a PDF still opens correctly.
 */
object FormatDetector {

    fun detect(files: FileAccess, uri: String, fileName: String, mimeType: String?, isDirectory: Boolean): BookFormat? {
        if (isDirectory) return BookFormat.IMAGE_FOLDER
        val byExtension = BookFormat.fromExtension(fileName.substringAfterLast('.', ""))
        val byMime = BookFormat.fromMime(mimeType)
        val byMagic = detectByMagic(files, uri)

        // Magic bytes win when they clearly disagree about the container.
        if (byMagic != null && byExtension != null && byMagic.family != byExtension.family) return byMagic
        return byExtension ?: byMime ?: byMagic
    }

    fun detectByMagic(files: FileAccess, uri: String): BookFormat? {
        val header = try {
            files.openInput(uri)?.use { input ->
                val buffer = ByteArray(HEADER_BYTES)
                var read = 0
                while (read < buffer.size) {
                    val count = input.read(buffer, read, buffer.size - read)
                    if (count < 0) break
                    read += count
                }
                buffer.copyOf(read)
            }
        } catch (e: Exception) {
            null
        } ?: return null

        return when {
            header.startsWith("%PDF") -> BookFormat.PDF
            header.startsWith("Rar!") -> BookFormat.CBR
            header.startsWith("PK") -> zipFlavor(header)
            header.startsWith("PNG") -> BookFormat.IMAGE
            header.size > 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> BookFormat.IMAGE
            header.startsWith("GIF8") -> BookFormat.IMAGE
            header.size > 12 && String(header, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> BookFormat.IMAGE
            header.startsWithIgnoreCase("<!doctype html") || header.startsWithIgnoreCase("<html") -> BookFormat.HTML
            else -> null
        }
    }

    /** EPUB files declare "mimetype" with the epub media type right after the local file header. */
    private fun zipFlavor(header: ByteArray): BookFormat {
        val text = String(header, Charsets.ISO_8859_1)
        return if (text.contains("application/epub+zip")) BookFormat.EPUB else BookFormat.CBZ
    }

    private fun ByteArray.startsWith(prefix: String): Boolean {
        if (size < prefix.length) return false
        return String(this, 0, prefix.length, Charsets.ISO_8859_1) == prefix
    }

    private fun ByteArray.startsWithIgnoreCase(prefix: String): Boolean {
        if (size < prefix.length) return false
        return String(this, 0, prefix.length, Charsets.ISO_8859_1).equals(prefix, ignoreCase = true)
    }

    private const val HEADER_BYTES = 128
}
