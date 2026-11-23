package app.folio.manga.source

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeException
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where one source's part of a search across sources stands. */
sealed interface SourceSearchState {
    data object Loading : SourceSearchState
    data class Done(val manga: List<SManga>) : SourceSearchState
    /** [needsWebView]: the site wants a person to pass a check, which opening it allows. */
    data class Failed(val message: String, val needsWebView: Boolean = false) : SourceSearchState
}

data class SourceSearchResult(
    val sourceId: Long,
    val sourceName: String,
    val lang: String,
    val state: SourceSearchState,
)

/**
 * Searches many sources for the same query at once.
 *
 * Each source answers on its own: one that is slow, behind Cloudflare or broken shows as loading
 * or failed in its own row and holds up nobody else. Only a few run at a time, since every source
 * is a different site and a phone has a modest number of connections to share.
 */
class GlobalSearch(
    private val maxConcurrent: Int = 5,
    private val perSourceTimeout: Duration = 60.seconds,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Emits every source's state, in [sources] order, each time one of them finishes. */
    fun search(sources: List<CatalogueSource>, query: String): Flow<List<SourceSearchResult>> = channelFlow {
        val results = sources.map { SourceSearchResult(it.id, it.name, it.lang, SourceSearchState.Loading) }
            .toMutableList()
        val lock = Mutex()
        send(results.toList())

        val permits = Semaphore(maxConcurrent)
        sources.forEachIndexed { index, source ->
            launch {
                val state = permits.withPermit { searchOne(source, query) }
                lock.withLock {
                    results[index] = results[index].copy(state = state)
                    send(results.toList())
                }
            }
        }
    }

    /** One source's answer to [query], with any failure turned into a [SourceSearchState.Failed]. */
    suspend fun searchOne(source: CatalogueSource, query: String): SourceSearchState = try {
        withTimeout(perSourceTimeout) {
            withContext(dispatcher) {
                SourceSearchState.Done(source.getSearchManga(1, query, source.getFilterList()).mangas)
            }
        }
    } catch (_: TimeoutCancellationException) {
        SourceSearchState.Failed(TIMED_OUT)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SourceSearchState.Failed(e.sourceErrorMessage(), needsWebView = e.needsWebView())
    } catch (e: LinkageError) {
        SourceSearchState.Failed(e.sourceErrorMessage())
    }

    private companion object {
        const val TIMED_OUT = "The source took too long to answer"
    }
}

/**
 * A source failure as a user can read it. A [LinkageError] means the extension was built against
 * extension API this app does not have; it is reported here rather than allowed to crash the app.
 */
fun Throwable.sourceErrorMessage(): String = when (this) {
    is LinkageError -> "This extension needs a feature Folio does not support yet (${message ?: javaClass.simpleName})"
    else -> message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName ?: "Could not reach the source"
}

/**
 * Whether opening the source's site would let the user get past what stopped this request:
 * a Cloudflare check, a site that refused us (403) or is shielding itself (503), or an extension
 * saying so itself, as many do ("Open in WebView to set necessary cookies").
 */
fun Throwable.needsWebView(): Boolean = generateSequence(this) { it.cause }.any { error ->
    error is CloudflareChallengeException ||
        (error is HttpException && error.code in WEBVIEW_HTTP_CODES) ||
        WEBVIEW_HINTS.any { hint -> error.message?.contains(hint, ignoreCase = true) == true }
}

private val WEBVIEW_HTTP_CODES = setOf(403, 503)

/** Wording extensions use when only the website can fix things: cookies, a login, a captcha. */
private val WEBVIEW_HINTS = listOf("webview", "web view", "captcha", "log in", "login")
