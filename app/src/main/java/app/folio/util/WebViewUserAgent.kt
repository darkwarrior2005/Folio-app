package app.folio.util

import android.webkit.WebView

/**
 * Gives a WebView the same user agent the source's OkHttp requests send. Cloudflare ties its
 * clearance cookie to the browser that earned it, so the two have to agree.
 */
fun WebView.matchUserAgentClientHints(userAgent: String) {
    if (userAgent.isNotBlank()) settings.userAgentString = userAgent
}

fun okhttp3.Headers.toWebViewHeaders(): Map<String, String> =
    names().associateWith { name -> get(name).orEmpty() }
