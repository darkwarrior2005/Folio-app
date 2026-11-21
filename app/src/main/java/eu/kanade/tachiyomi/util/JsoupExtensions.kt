package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val jsoupNetworkHelper: NetworkHelper by lazy { Injekt.get<NetworkHelper>() }

fun okhttp3.Response.asJsoup(html: String? = null): Document {
    return Jsoup.parse(html ?: body.string(), request.url.toString())
}

fun OkHttpClient.newCallWithJsoup(request: Request): Document {
    newCall(request).execute().use { response -> return response.asJsoup() }
}

fun String.parseAsHtml(baseUrl: String = ""): Document = Jsoup.parse(this, baseUrl)

fun getUrlWithoutDomain(orig: String): String {
    return try {
        val uri = java.net.URI(orig.replace(" ", "%20"))
        var out = uri.path
        if (uri.query != null) out += "?" + uri.query
        if (uri.fragment != null) out += "#" + uri.fragment
        out
    } catch (_: Exception) {
        orig
    }
}

fun headersOf(vararg pairs: Pair<String, String>): Headers {
    val builder = Headers.Builder()
    pairs.forEach { (name, value) -> builder.add(name, value) }
    return builder.build()
}
