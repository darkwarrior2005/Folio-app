package eu.kanade.tachiyomi.network

import okhttp3.Response

/**
 * Thrown for HTTP codes OkHttp does not consider successful, so the message is the same
 * everywhere in the app and in extensions.
 *
 * @see Response.isSuccessful
 * @since tachiyomix 1.6
 */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
