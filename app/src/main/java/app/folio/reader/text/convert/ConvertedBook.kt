package app.folio.reader.text.convert

import java.util.Base64

/**
 * A book in a format the text reader does not read directly, turned into one HTML document.
 * Pictures travel inside it as data URIs; [cover] is the book's own cover image, when it has one.
 */
class ConvertedBook(
    val html: String,
    val title: String? = null,
    val author: String? = null,
    val cover: ByteArray? = null,
)

/** The book cannot be converted, and why, in terms the reader can show. */
class ConversionException(val reason: Reason, message: String? = null) : Exception(message ?: reason.name) {
    enum class Reason { DRM, UNSUPPORTED, DAMAGED }
}

/**
 * Turns embedded pictures into data URIs, up to a total budget so that a book full of large
 * scans stays openable: pictures past the budget are left out.
 */
class ImageBudget(private var remaining: Long = DEFAULT_BUDGET) {

    fun dataUri(bytes: ByteArray?, declaredMime: String? = null): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val mime = sniff(bytes) ?: declaredMime?.takeIf { it.startsWith("image/") } ?: return null
        if (bytes.size > remaining) return null
        remaining -= bytes.size
        return "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
    }

    companion object {
        const val DEFAULT_BUDGET = 16L * 1024 * 1024

        fun sniff(bytes: ByteArray): String? {
            fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
            return when {
                at(0) == 0xFF && at(1) == 0xD8 -> "image/jpeg"
                at(0) == 0x89 && at(1) == 'P'.code && at(2) == 'N'.code && at(3) == 'G'.code -> "image/png"
                at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code -> "image/gif"
                at(0) == 'B'.code && at(1) == 'M'.code -> "image/bmp"
                bytes.size > 12 && String(bytes, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> "image/webp"
                bytes.size > 5 && String(bytes, 0, 5, Charsets.ISO_8859_1).let { it == "<?xml" || it.startsWith("<svg") } -> "image/svg+xml"
                else -> null
            }
        }
    }
}

internal fun escape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
