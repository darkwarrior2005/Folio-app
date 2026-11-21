package app.folio.reader.text.convert

/** The common result of converting MOBI/AZW3, FB2, DOCX, ODT or RTF to HTML. */
data class ConvertedBook(
    val html: String,
    val title: String? = null,
    val author: String? = null,
    /** Raw cover image bytes, if the source embedded one. */
    val cover: ByteArray? = null,
)

enum class ConversionReason { DRM, UNSUPPORTED, DAMAGED }

class ConversionException(val reason: ConversionReason, message: String? = null) :
    Exception(message ?: reason.name)

/** Keeps embedded pictures, as data: URIs, within a byte budget so one book can't blow up memory. */
class ImageBudget(private val maxTotalBytes: Int = 16 * 1024 * 1024) {
    private var used = 0

    fun tryAdd(bytes: ByteArray, mimeType: String): String? {
        if (used + bytes.size > maxTotalBytes) return null
        used += bytes.size
        val base64 = java.util.Base64.getEncoder().encodeToString(bytes)
        return "data:$mimeType;base64,$base64"
    }
}

fun guessImageMimeType(bytes: ByteArray): String = when {
    bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
    bytes.size >= 6 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "image/gif"
    bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" -> "image/webp"
    else -> "image/jpeg"
}
