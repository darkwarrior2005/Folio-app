package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Rethrows anything a later interceptor throws as an IOException, so a misbehaving source
 * cannot bring the app down. Keep it first in the chain.
 */
class UncaughtExceptionInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response = try {
        chain.proceed(chain.request())
    } catch (e: Exception) {
        if (e is IOException) throw e else throw IOException(e)
    }
}
