package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Request

fun GET(
    url: String,
    headers: Headers = Headers.Builder().build(),
    cache: CacheControl = CacheControl.FORCE_NETWORK,
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .cacheControl(cache)
    .build()

fun POST(
    url: String,
    headers: Headers = Headers.Builder().build(),
    body: okhttp3.RequestBody = okhttp3.FormBody.Builder().build(),
    cache: CacheControl = CacheControl.FORCE_NETWORK,
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .cacheControl(cache)
    .post(body)
    .build()

fun PUT(
    url: String,
    headers: Headers = Headers.Builder().build(),
    body: okhttp3.RequestBody = okhttp3.FormBody.Builder().build(),
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .put(body)
    .build()

fun DELETE(
    url: String,
    headers: Headers = Headers.Builder().build(),
): Request = Request.Builder()
    .url(url)
    .headers(headers)
    .delete()
    .build()
