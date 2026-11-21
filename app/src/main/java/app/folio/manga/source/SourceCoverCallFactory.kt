package app.folio.manga.source

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Loads a manga cover through its own source's OkHttp client and headers, instead of a plain
 * shared client - some sources need their own referer/cookies even just to fetch an image. Coil
 * reads the `X-Folio-Source` header [MangaCover] sets to know which source to use, then this
 * factory strips it before the real request goes out.
 */
class SourceCoverCallFactory(
    private val sourceManager: SourceManager,
    private val fallback: OkHttpClient,
) : Call.Factory {

    override fun newCall(request: Request): Call {
        val sourceId = request.header(SOURCE_HEADER)?.toLongOrNull()
        val client = sourceId?.let { sourceManager.get(it) }
            ?.let { it as? eu.kanade.tachiyomi.source.online.HttpSource }
            ?.client
            ?: fallback
        val stripped = request.newBuilder().removeHeader(SOURCE_HEADER).build()
        return client.newCall(stripped)
    }

    companion object {
        const val SOURCE_HEADER = "X-Folio-Source"
    }
}
