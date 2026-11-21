package app.folio.data.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import app.folio.AppContainer

/**
 * Forgets manga browsing on request, or on every app start when `PrivacySettings.forgetBrowsing`
 * is on (the default): manga that were only browsed (never favorited, nothing downloaded or
 * read), the sources' cached network responses, and cached covers. With [includeSiteData] it
 * also signs the user out of source websites - cookies and WebView site storage.
 */
class BrowsingData(private val context: Context, private val container: AppContainer) {

    suspend fun forget(includeSiteData: Boolean) {
        container.manga.deleteBrowsedOnly()
        clearNetworkCache()
        clearCoverCache()
        if (includeSiteData) {
            clearCookiesAndSiteData()
        }
    }

    private fun clearNetworkCache() {
        java.io.File(context.cacheDir, "network_cache").deleteRecursively()
    }

    private fun clearCoverCache() {
        java.io.File(context.cacheDir, "image_cache").deleteRecursively()
    }

    private fun clearCookiesAndSiteData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }
}
