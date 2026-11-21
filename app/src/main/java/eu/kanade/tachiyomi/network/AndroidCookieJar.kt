package eu.kanade.tachiyomi.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * A cookie jar backed by the system [CookieManager], so Folio's OkHttp requests and the WebView
 * (used for Cloudflare and "Open the site") always share the same cookies.
 */
class AndroidCookieJar : CookieJar {

    private val manager: CookieManager = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        for (cookie in cookies) {
            manager.setCookie(urlString, cookie.toString())
        }
        manager.flush()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookies = manager.getCookie(url.toString())
        return if (!cookies.isNullOrEmpty()) {
            cookies.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
        } else {
            emptyList()
        }
    }

    fun get(url: HttpUrl): List<Cookie> = loadForRequest(url)

    fun remove(url: HttpUrl, cookieNames: List<String>? = null, maxAge: Int = -1) {
        val urlString = url.toString()
        val cookies = manager.getCookie(urlString) ?: return
        cookies.split(";").forEach { raw ->
            val name = raw.trim().substringBefore('=')
            if (cookieNames == null || name in cookieNames) {
                manager.setCookie(urlString, "$name=; Max-Age=$maxAge")
            }
        }
        manager.flush()
    }

    fun removeAll() {
        manager.removeAllCookies(null)
        manager.flush()
    }
}
