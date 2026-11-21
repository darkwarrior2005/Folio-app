package eu.kanade.tachiyomi.util

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.schedulers.Schedulers
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Subscribes on Rx's own IO scheduler, not the caller's dispatcher: old Rx-based sources would
 * otherwise run their whole chain, network call included, on the coroutine dispatcher the caller
 * happened to be on (often the main thread).
 */
suspend fun <T> Observable<T>.awaitSingle(): T = suspendCancellableCoroutine { continuation ->
    val subscription = subscribeOn(Schedulers.io())
        .single()
        .subscribe(
            { value -> continuation.resume(value) },
            { error -> continuation.resumeWithException(error) },
        )
    continuation.invokeOnCancellation { subscription.unsubscribe() }
}
