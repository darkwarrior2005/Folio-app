package app.folio.manga.source

import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Sends cover requests through the source they came from, as Mihon does: with the source's own
 * HTTP client (its cookies, Cloudflare handling, rate limits and interceptors) and its headers
 * (Referer, User-Agent). Many sites refuse covers to requests that do not look like their pages.
 *
 * A cover request names its source in [SOURCE_HEADER] (set by MangaCover); the header is taken
 * off before anything is sent. Requests without one, or for a source that is not loaded, go out
 * through a plain client.
 */
class SourceCoverCallFactory(
    private val sources: () -> SourceManager,
    private val fallback: () -> OkHttpClient,
) : Call.Factory {

    override fun newCall(request: Request): Call {
        val sourceId = request.header(SOURCE_HEADER)?.toLongOrNull()
        val builder = request.newBuilder().removeHeader(SOURCE_HEADER)
        val source = sourceId?.let { sources().get(it) } as? HttpSource
            ?: return fallback().newCall(builder.build())

        return runCatching {
            source.headers.forEach { (name, value) ->
                if (request.header(name) == null) builder.header(name, value)
            }
            source.client.newCall(builder.build())
        }.getOrElse {
            // An extension that cannot even hand over its client still gets a plain attempt.
            fallback().newCall(request.newBuilder().removeHeader(SOURCE_HEADER).build())
        }
    }

    companion object {
        const val SOURCE_HEADER = "X-Folio-Source"
    }
}
