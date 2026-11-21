package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One shared OkHttp stack for every manga source. [MangaSourceModule] is the only thing that
 * constructs this (through Injekt), so every source sees the same cookie jar and interceptors.
 *
 * Keiyoushi's base `HttpSource` class refuses to load unless the client's interceptors include
 * ones literally named `UncaughtExceptionInterceptor`, `UserAgentInterceptor` and
 * `CloudflareInterceptor` (added in [client] by the caller, see [MangaSourceModule]) - keep those
 * class names exactly as they are.
 */
class NetworkHelper(private val context: Context, private val userAgentProvider: () -> String) {

    val cookieJar = AndroidCookieJar()

    /** The base client; callers add the Cloudflare interceptor last so it sees 403s and retries. */
    fun baseClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30.seconds)
        .readTimeout(30.seconds)
        .callTimeout(2.minutes)
        .cache(
            Cache(
                directory = File(context.cacheDir, "network_cache"),
                maxSize = 5L * 1024 * 1024,
            ),
        )
        .addInterceptor(UncaughtExceptionInterceptor())
        .addInterceptor(UserAgentInterceptor(userAgentProvider))

    val client: OkHttpClient by lazy { baseClientBuilder().build() }

    fun defaultUserAgentProvider(): String = userAgentProvider()

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
