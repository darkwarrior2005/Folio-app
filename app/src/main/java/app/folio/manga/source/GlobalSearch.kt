package app.folio.manga.source

import eu.kanade.tachiyomi.network.interceptor.CloudflareChallengeException

/**
 * Searches every enabled source for a query, library matches first then one row per source as
 * it answers (at most [MAX_CONCURRENT] at a time, each capped at [TIMEOUT_MS]).
 */
object GlobalSearch {

    const val MAX_CONCURRENT = 5
    const val TIMEOUT_MS = 60_000L

    /**
     * Whether a failed search on this source is plausibly fixable by the user opening the site
     * themselves ("Open the site"), as opposed to a dead source or a genuine no-results search.
     */
    fun needsWebView(error: Throwable): Boolean {
        if (error is CloudflareChallengeException) return true
        val httpCode = (error as? eu.kanade.tachiyomi.network.HttpException)?.code
        if (httpCode == 403 || httpCode == 503) return true
        val message = error.message?.lowercase().orEmpty()
        return listOf("webview", "captcha", "login", "cloudflare").any { it in message }
    }
}
