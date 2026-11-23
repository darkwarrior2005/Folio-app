package eu.kanade.tachiyomi.network

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import java.io.IOException

class ProgressResponseBody(
    private val responseBody: ResponseBody,
    private val progressListener: ProgressListener,
    // Bytes already on disk, for a resumed (HTTP 206) download.
    private val existingSize: Long,
) : ResponseBody() {

    private val bufferedSource: BufferedSource by lazy { source(responseBody.source()).buffer() }

    override fun contentType(): MediaType? = responseBody.contentType()

    override fun contentLength(): Long = responseBody.contentLength()

    override fun source(): BufferedSource = bufferedSource

    private fun source(source: Source): Source = object : ForwardingSource(source) {
        var totalBytesRead = existingSize

        @Throws(IOException::class)
        override fun read(sink: Buffer, byteCount: Long): Long {
            val bytesRead = super.read(sink, byteCount)
            // read() returns the bytes read, or -1 once the source is exhausted.
            totalBytesRead += if (bytesRead != -1L) bytesRead else 0

            // contentLength() is -1 when the server sends no Content-Length.
            val totalLength = responseBody.contentLength().let {
                if (it != -1L) it + existingSize else -1L
            }

            progressListener.update(totalBytesRead, totalLength, bytesRead == -1L)
            return bytesRead
        }
    }
}
