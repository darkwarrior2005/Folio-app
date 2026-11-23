package eu.kanade.tachiyomi.util.lang

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.schedulers.Schedulers
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Awaits the single value of this observable.
 *
 * The source API still exposes the old RxJava 1 methods for extensions that have not moved to
 * the suspend API, and this bridges them back into coroutines.
 *
 * Those observables run their request synchronously on whichever thread subscribes, so this
 * subscribes on Rx's IO pool: awaited from the main thread they would otherwise hit
 * NetworkOnMainThreadException, or freeze while a Cloudflare check waits on the main thread.
 */
suspend fun <T> Observable<T>.awaitSingle(): T = suspendCancellableCoroutine { continuation ->
    val subscription = subscribeOn(Schedulers.io()).single().subscribe(
        { value -> if (continuation.isActive) continuation.resume(value) },
        { error -> if (continuation.isActive) continuation.resumeWithException(error) },
    )
    continuation.invokeOnCancellation { subscription.unsubscribe() }
}
