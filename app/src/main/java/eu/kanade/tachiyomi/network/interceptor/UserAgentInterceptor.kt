package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor(private val defaultUserAgentProvider: () -> String) : Interceptor {

    private var currentUserAgent: String = defaultUserAgentProvider()

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.header("User-Agent").isNullOrEmpty()) {
            val newRequest = originalRequest.newBuilder()
                .removeHeader("User-Agent")
                .addHeader("User-Agent", currentUserAgent)
                .build()
            return chain.proceed(newRequest)
        }
        return chain.proceed(originalRequest)
    }
}
