package eu.kanade.tachiyomi.network.interceptor

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Like [RateLimitInterceptor], but only requests to [host] are throttled; others pass through. */
class SpecificHostRateLimitInterceptor(
    private val host: HttpUrl,
    private val permits: Int,
    period: Duration,
) : Interceptor {

    private val periodNanos = period.inWholeNanoseconds
    private val requestQueue = ArrayDeque<Long>()
    private val fairLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != host.host) {
            return chain.proceed(request)
        }
        synchronized(fairLock) {
            while (requestQueue.size >= permits) {
                val now = System.nanoTime()
                val remaining = periodNanos - (now - requestQueue.first())
                if (remaining <= 0) {
                    requestQueue.removeFirst()
                    continue
                }
                TimeUnit.NANOSECONDS.sleep(remaining)
                requestQueue.removeFirst()
            }
            requestQueue.addLast(System.nanoTime())
        }
        return chain.proceed(request)
    }
}
