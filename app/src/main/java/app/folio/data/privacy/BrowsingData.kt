package app.folio.data.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import app.folio.data.repo.MangaRepository
import coil3.SingletonImageLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What browsing and searching leave behind on the phone, and the means to wipe it.
 *
 * Search terms themselves are never written anywhere: they live in memory for as long as a
 * screen shows them. What does reach storage is the trail they lead to: manga opened from a
 * search but never added, the sites' responses in the network cache, their covers in the image
 * cache, and what a source's website stored while it was open.
 */
class BrowsingData(
    private val context: Context,
    private val manga: MangaRepository,
    private val network: () -> NetworkHelper,
) {

    /**
     * Forgets the browsing trail. Manga in the library, and anything downloaded, stay.
     *
     * [includeSiteData] also signs out of every source's website: cookies and site storage go,
     * including the passes Cloudflare-protected sites hand out, which will be asked for again.
     */
    suspend fun forget(includeSiteData: Boolean = false) {
        withContext(Dispatchers.IO) {
            runCatching { manga.pruneBrowsed() }
            runCatching { network().client.cache?.evictAll() }
            runCatching { SingletonImageLoader.get(context).diskCache?.clear() }
        }
        runCatching { SingletonImageLoader.get(context).memoryCache?.clear() }
        if (includeSiteData) {
            withContext(Dispatchers.Main) {
                runCatching {
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
            }
        }
    }
}
