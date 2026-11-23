package eu.kanade.tachiyomi.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Producer
import rx.Subscription
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resumeWithException

val jsonMime = "application/json; charset=utf-8".toMediaType()

@Deprecated("Use suspend APIs instead")
fun Call.asObservable(): Observable<Response> {
    return Observable.unsafeCreate { subscriber ->
        // Call is one-shot, so each subscriber gets its own clone.
        val call = clone()

        // Handles both unsubscription and backpressure.
        val requestArbiter = object : Producer, Subscription {
            val started = AtomicBoolean(false)

            override fun request(n: Long) {
                if (n == 0L || !started.compareAndSet(false, true)) return

                try {
                    val response = call.execute()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onNext(response)
                        subscriber.onCompleted()
                    }
                } catch (e: Exception) {
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(e)
                    }
                }
            }

            override fun unsubscribe() {
                call.cancel()
            }

            override fun isUnsubscribed(): Boolean = call.isCanceled()
        }

        subscriber.add(requestArbiter)
        subscriber.setProducer(requestArbiter)
    }
}

@Deprecated("Use suspend APIs instead")
fun Call.asObservableSuccess(): Observable<Response> {
    @Suppress("DEPRECATION")
    return asObservable().doOnNext { response ->
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
    }
}

private suspend fun Call.await(callStack: Array<StackTraceElement>): Response {
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (_: Throwable) {
                // Already finished or cancelled.
            }
        }

        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    // Keep the caller's stack trace; OkHttp's own is the dispatcher thread.
                    continuation.resumeWithException(IOException(e.message, e).apply { stackTrace = callStack })
                }

                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            },
        )
    }
}

suspend fun Call.await(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    return await(callStack)
}

/**
 * Like [await], but throws [HttpException] when the response is not successful.
 */
suspend fun Call.awaitSuccess(): Response {
    val callStack = Exception().stackTrace.run { copyOfRange(1, size) }
    val response = await(callStack)
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code).apply { stackTrace = callStack }
    }
    return response
}

/**
 * A call that bypasses the HTTP cache and reports download progress to [listener]. Used for
 * page images, where the progress drives the reader's loading indicator.
 */
fun OkHttpClient.newCachelessCallWithProgress(
    request: Request,
    listener: ProgressListener,
    existingSize: Long = 0L,
): Call {
    val progressClient = newBuilder()
        .cache(null)
        .addNetworkInterceptor { chain ->
            val newRequest = chain.request().newBuilder()
                .apply {
                    if (existingSize > 0 && chain.request().header("Range") == null) {
                        header("Range", "bytes=$existingSize-")
                    }
                }
                .build()

            val originalResponse = chain.proceed(newRequest)
            // Only count what is already on disk when the server honoured the range request.
            val actualExistingSize = if (originalResponse.code == 206) existingSize else 0L
            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body, listener, actualExistingSize))
                .build()
        }
        .build()

    return progressClient.newCall(request)
}

inline fun <reified T> Response.parseAs(): T {
    val json = Injekt.get<Json>()
    return body.use { json.decodeFromString<T>(it.string()) }
}
