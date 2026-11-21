package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.folio.util.matchUserAgentClientHints
import app.folio.util.toWebViewHeaders
import eu.kanade.tachiyomi.network.AndroidCookieJar
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Solves a Cloudflare browser check in an off-screen WebView and retries the request with the
 * `cf_clearance` cookie it earns. A check that needs an actual click (a captcha, "verify you are
 * human") can't be solved here and fails with [CloudflareChallengeException]; "Open the site"
 * (SourceWebViewScreen) is where the user solves those.
 *
 * The class name matters: Keiyoushi's base HttpSource refuses to start a client unless its
 * interceptors include one literally named CloudflareInterceptor.
 */
class CloudflareInterceptor(
    private val context: Context,
    private val cookieJar: AndroidCookieJar,
    private val scope: CoroutineScope,
    private val defaultUserAgentProvider: () -> String,
) : Interceptor {

    private val executor = androidx.core.content.ContextCompat.getMainExecutor(context)

    // Concurrent requests to the same host share one WebView solve instead of each opening one.
    private val hostLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val lock = hostLocks.computeIfAbsent(request.url.host) { ReentrantReadWriteLock() }

        val outcome = lock.read {
            val response = chain.proceed(request)
            if (!looksLikeChallenge(response)) return response
            response to clearanceCookie(request.url)
        }

        lock.write {
            val (response, previousClearance) = outcome
            if (clearanceCookie(request.url) != previousClearance && clearanceCookie(request.url) != null) {
                // Solved by another request to this host while we waited for the write lock.
                response.close()
            } else {
                response.close()
                cookieJar.remove(request.url, COOKIE_NAMES)
                resolveWithWebView(request)
            }
        }

        return chain.proceed(request)
    }

    private fun looksLikeChallenge(response: Response): Boolean {
        if (response.code !in intArrayOf(403, 503)) return false
        val server = response.header("Server").orEmpty()
        return server in SERVER_CHECK || response.header("cf-mitigated") == "challenge"
    }

    private fun clearanceCookie(url: HttpUrl): String? =
        cookieJar.get(url).firstOrNull { it.name == "cf_clearance" }?.value

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request) {
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var failed = false

        val challengeUrl = if (originalRequest.method == "GET") {
            originalRequest.url.toString()
        } else {
            originalRequest.url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()
        }
        val headers = originalRequest.headers.toWebViewHeaders()

        executor.execute {
            val view = WebView(context)
            webView = view
            view.settings.javaScriptEnabled = true
            view.matchUserAgentClientHints(defaultUserAgentProvider())

            view.addJavascriptInterface(
                object {
                    @Suppress("unused")
                    @JavascriptInterface
                    fun postMessage(event: String) {
                        if (event == "fail" || event == "interactiveBegin") {
                            failed = true
                            latch.countDown()
                        }
                    }
                },
                "folio",
            )

            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(webView: WebView, url: String) {
                    if (clearanceCookie(originalRequest.url) != null) {
                        latch.countDown()
                    } else if (url == challengeUrl) {
                        // Still on the challenge page after it loaded: nothing left to wait for.
                        failed = true
                        latch.countDown()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true && errorResponse?.responseHeaders?.get("cf-mitigated") != "challenge") {
                        failed = true
                        latch.countDown()
                    }
                }
            }

            view.loadUrl(challengeUrl, headers)
        }

        latch.await(30, TimeUnit.SECONDS)

        executor.execute {
            webView?.apply {
                stopLoading()
                removeJavascriptInterface("folio")
                destroy()
            }
        }

        if (failed || clearanceCookie(originalRequest.url) == null) {
            cookieJar.remove(originalRequest.url, COOKIE_NAMES)
            throw IOException(CloudflareChallengeException())
        }
    }

    companion object {
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        private val COOKIE_NAMES = listOf("cf_clearance")
    }
}

/** Thrown when the challenge needed real user interaction and couldn't be solved headlessly. */
class CloudflareChallengeException : Exception("This site needs you to open it once to continue")
