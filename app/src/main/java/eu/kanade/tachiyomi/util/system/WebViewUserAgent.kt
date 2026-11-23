package eu.kanade.tachiyomi.util.system

import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

private const val WEBVIEW_BRAND = "Android WebView"
private const val CHROMIUM_BRAND = "Chromium"
private const val CHROME_BRAND = "Google Chrome"
private val CHROME_VERSION_REGEX = """Chrome/(\d+)(\.[\d.]+)?""".toRegex()

/**
 * Sets the user agent and the matching client hints. Without the hints `Sec-CH-UA` keeps
 * advertising the real WebView, contradicting the user agent, and Cloudflare refuses the pass
 * the WebView earns when the source's own requests later present it.
 *
 * Adapted from Mihon (Apache-2.0).
 */
fun WebView.setUserAgentWithClientHints(userAgent: String) {
    settings.userAgentString = userAgent
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) return

    val versionMatch = CHROME_VERSION_REGEX.find(userAgent) ?: return
    val majorVersion = versionMatch.groupValues[1]
    val fullVersion = majorVersion + versionMatch.groupValues[2].ifEmpty { ".0.0.0" }

    try {
        val metadata = WebSettingsCompat.getUserAgentMetadata(settings)
        val brands = metadata.brandVersionList.map { brandVersion ->
            val brand = when (brandVersion.brand) {
                WEBVIEW_BRAND -> CHROME_BRAND
                CHROMIUM_BRAND -> CHROMIUM_BRAND
                else -> return@map brandVersion
            }
            UserAgentMetadata.BrandVersion.Builder()
                .setBrand(brand)
                .setMajorVersion(majorVersion)
                .setFullVersion(fullVersion)
                .build()
        }
        WebSettingsCompat.setUserAgentMetadata(
            settings,
            UserAgentMetadata.Builder(metadata)
                .setBrandVersionList(brands)
                .setFullVersion(fullVersion)
                .build(),
        )
    } catch (_: Exception) {
        // The plain user agent above still applies.
    }
}
