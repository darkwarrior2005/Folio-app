package app.folio.data.files

import java.io.InputStream
import java.security.MessageDigest

/**
 * Identity of a physical file, used for duplicate detection and for confirming a relocated file
 * is the same book. Hashing whole multi-hundred-megabyte PDFs on import would be slow, so we hash
 * the head and tail plus the exact size, which is enough to tell books apart in a personal library.
 */
object Hashing {

    const val SAMPLE_BYTES = 1 shl 20 // 1 MiB

    fun quickHash(input: InputStream, size: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(size.toString().toByteArray())
        input.use { stream ->
            val head = ByteArray(SAMPLE_BYTES)
            val headRead = stream.readAtMost(head)
            digest.update(head, 0, headRead)
            if (size > 2L * SAMPLE_BYTES) {
                var toSkip = size - SAMPLE_BYTES - headRead
                while (toSkip > 0) {
                    val skipped = stream.skip(toSkip)
                    if (skipped <= 0) break
                    toSkip -= skipped
                }
                val tail = ByteArray(SAMPLE_BYTES)
                val tailRead = stream.readAtMost(tail)
                digest.update(tail, 0, tailRead)
            }
        }
        return digest.digest().toHex()
    }

    private fun InputStream.readAtMost(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHex()
}
