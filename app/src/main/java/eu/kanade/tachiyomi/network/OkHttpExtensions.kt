package eu.kanade.tachiyomi.network

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Emitter
import rx.Observable
import rx.Subscription
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    val callback = object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }

        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }
    }
    enqueue(callback)
    continuation.cancelOnCancellation(callback, this)
}

private fun CancellableContinuation<*>.cancelOnCancellation(callback: Callback, call: Call) {
    invokeOnCancellation {
        try {
            call.cancel()
        } catch (_: Throwable) {
        }
    }
}

suspend fun Call.awaitSuccess(): Response {
    val response = await()
    if (!response.isSuccessful) {
        response.close()
        throw HttpException(response.code)
    }
    return response
}

fun Call.asObservable(): Observable<Response> {
    return Observable.create(
        { emitter ->
            val call = this.clone()
            val callback = object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    emitter.onNext(response)
                    emitter.onCompleted()
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (call.isCanceled()) return
                    emitter.onError(e)
                }
            }
            emitter.setCancellation { call.cancel() }
            call.enqueue(callback)
        },
        Emitter.BackpressureMode.NONE,
    )
}

fun Call.asObservableSuccess(): Observable<Response> {
    return asObservable().doOnNext { response ->
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
    }
}

fun OkHttpClient.newCachelessCallWithProgress(request: Request, listener: ProgressListener): Call {
    val progressClient = newBuilder()
        .cache(null)
        .addNetworkInterceptor { chain ->
            val original = chain.proceed(chain.request())
            original.newBuilder()
                .body(ProgressResponseBody(original.body, listener))
                .build()
        }
        .build()
    return progressClient.newCall(request)
}

fun Subscription.unsubscribeIfNeeded() {
    if (!isUnsubscribed) unsubscribe()
}

typealias PageList = List<Page>
