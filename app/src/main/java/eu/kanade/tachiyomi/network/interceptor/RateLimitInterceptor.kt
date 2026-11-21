package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/** Limits a client to [permits] requests per [period], across every host it talks to. */
class RateLimitInterceptor(
    private val permits: Int,
    period: Duration,
) : Interceptor {

    private val periodNanos = period.inWholeNanoseconds
    private val requestQueue = ArrayDeque<Long>()
    private val fairLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        synchronized(fairLock) {
            wait(permits, requestQueue, periodNanos)
            requestQueue.addLast(System.nanoTime())
        }
        return chain.proceed(chain.request())
    }

    private fun wait(permits: Int, queue: ArrayDeque<Long>, periodNanos: Long) {
        while (queue.size >= permits) {
            val now = System.nanoTime()
            val oldest = queue.first()
            val remaining = periodNanos - (now - oldest)
            if (remaining <= 0) {
                queue.removeFirst()
                continue
            }
            TimeUnit.NANOSECONDS.sleep(remaining)
            queue.removeFirst()
        }
    }
}
