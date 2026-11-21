package app.folio.util

import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Cloudflare ties its clearance cookie to the browser that earned it, including the client-hint
 * headers a modern Chrome sends. Setting the WebView's user agent alone isn't enough - the hints
 * have to agree with it too, or some sites re-challenge every request.
 */
fun WebView.matchUserAgentClientHints(userAgent: String) {
    settings.userAgentString = userAgent
    if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
        val current = WebSettingsCompat.getUserAgentMetadata(settings)
        WebSettingsCompat.setUserAgentMetadata(
            settings,
            androidx.webkit.UserAgentMetadata.Builder()
                .setBrands(current.brandVersionList)
                .setFullVersion(current.fullVersion)
                .setPlatform(current.platform)
                .setMobile(current.isMobile)
                .build(),
        )
    }
}

fun okhttp3.Headers.toWebViewHeaders(): Map<String, String> =
    names().associateWith { name -> get(name).orEmpty() }
