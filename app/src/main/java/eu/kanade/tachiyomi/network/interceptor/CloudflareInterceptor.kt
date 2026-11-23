package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import app.folio.R
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.setUserAgentWithClientHints
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Gets past Cloudflare's "checking your browser" page by loading it in an off-screen WebView,
 * which runs the challenge and receives the `cf_clearance` cookie; the request is then retried
 * with that cookie, shared through [AndroidCookieJar].
 *
 * Extensions built on Keiyoushi's `KeiSource` refuse to run unless the shared client carries an
 * interceptor with exactly this class name, so it must not be renamed.
 *
 * A challenge that needs the user to click something cannot be solved here: the request fails with
 * [CloudflareChallengeException], which the UI answers by offering to open the site, where the
 * user can pass the check themselves and leave the cookie behind for the next try.
 *
 * Adapted from Mihon (Apache-2.0).
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    private val executor = ContextCompat.getMainExecutor(context)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!isChallenge(response) || !supportsWebView()) return response

        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieManager.get(request.url).firstOrNull { it.name == CLEARANCE_COOKIE }
            resolveWithWebView(request, oldCookie)
            return chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            // OkHttp's async calls only report IOExceptions; anything else would crash the app.
            throw CloudflareChallengeException(
                host = request.url.host,
                message = context.getString(R.string.manga_cloudflare_failed, request.url.host),
            )
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e)
        }
    }

    /** Cloudflare's documented way to recognise a challenge page. */
    private fun isChallenge(response: Response): Boolean =
        response.header("cf-mitigated") == "challenge" && response.header("Server") in SERVER_CHECK

    private fun supportsWebView(): Boolean = try {
        // Throws when the WebView package is missing or being updated.
        CookieManager.getInstance()
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)
    } catch (_: Throwable) {
        false
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        // OkHttp interceptors are synchronous, so this thread waits for the WebView.
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var challengeFound = false
        var bypassed = false

        val requestUrl = originalRequest.url.toString()
        val headers = safeHeaders(originalRequest.headers)

        executor.execute {
            val view = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                // An empty user agent makes Chromium fall back to its own, which Cloudflare then
                // sees disagreeing with the one the source sends.
                setUserAgentWithClientHints(originalRequest.header("User-Agent") ?: defaultUserAgentProvider())
            }
            webView = view

            view.addJavascriptInterface(
                object {
                    @Suppress("unused")
                    @JavascriptInterface
                    fun interactiveDetected() {
                        // This challenge wants a click; nothing more can be done off-screen.
                        latch.countDown()
                    }
                },
                JS_BRIDGE,
            )

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    val cleared = cookieManager.get(requestUrl.toHttpUrl())
                        .firstOrNull { it.name == CLEARANCE_COOKIE }
                        .let { it != null && it != oldCookie }
                    if (cleared) {
                        bypassed = true
                        latch.countDown()
                    }

                    if (url == requestUrl) {
                        if (!challengeFound) {
                            // The page loaded without a challenge this time; let the retry go.
                            latch.countDown()
                        } else {
                            view.evaluateJavascript(
                                """
                                    addEventListener("message", ({data}) => {
                                        if (data?.source === "cloudflare-challenge" && data?.event === "interactiveBegin") {
                                            $JS_BRIDGE.interactiveDetected();
                                        }
                                    })
                                """.trimIndent(),
                                null,
                            )
                        }
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.responseHeaders?.get("cf-mitigated") == "challenge") {
                            challengeFound = true
                        } else {
                            latch.countDown()
                        }
                    }
                }
            }

            view.loadUrl(requestUrl, headers)
        }

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        executor.execute {
            webView?.run {
                stopLoading()
                // The challenge page is not worth keeping; the clearance cookie is what matters.
                clearCache(true)
                clearHistory()
                destroy()
            }
        }

        if (!bypassed) throw CloudflareBypassException()
    }

    /** Headers Chromium refuses to send ([net::ERR_INVALID_ARGUMENT]) are dropped. */
    private fun safeHeaders(headers: Headers): Map<String, String> = headers
        .filter { (name, value) -> isRequestHeaderSafe(name, value) }
        .groupBy(keySelector = { (name, _) -> name }) { (_, value) -> value }
        .mapValues { it.value.firstOrNull().orEmpty() }

    private fun isRequestHeaderSafe(name: String, value: String): Boolean {
        val lowerName = name.lowercase(Locale.ENGLISH)
        if (lowerName in UNSAFE_HEADERS || lowerName.startsWith("proxy-")) return false
        return !(lowerName == "connection" && value.lowercase(Locale.ENGLISH) == "upgrade")
    }

    private class CloudflareBypassException : Exception()

    private companion object {
        const val CLEARANCE_COOKIE = "cf_clearance"
        const val JS_BRIDGE = "folioCloudflare"
        const val TIMEOUT_SECONDS = 30L
        val COOKIE_NAMES = listOf(CLEARANCE_COOKIE)
        val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        val UNSAFE_HEADERS = listOf(
            "content-length", "host", "trailer", "te", "upgrade", "cookie2", "keep-alive",
            "transfer-encoding", "set-cookie",
        )
    }
}

/** Cloudflare wants a person to pass its check; the site has to be opened for that. */
class CloudflareChallengeException(val host: String, message: String) : IOException(message)
