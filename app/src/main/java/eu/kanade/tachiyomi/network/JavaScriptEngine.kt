package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs

/**
 * Runs a source's own site-check script (Cloudflare-adjacent anti-bot JS that doesn't need a
 * full WebView) in a sandboxed QuickJS context. Extensions obtain this through Injekt.
 */
class JavaScriptEngine(private val context: Context) {

    suspend fun evaluate(script: String): String? = kotlinx.coroutines.withContext(
        kotlinx.coroutines.Dispatchers.Default,
    ) {
        val quickJs = QuickJs.create()
        try {
            quickJs.evaluate(script)?.toString()
        } finally {
            quickJs.close()
        }
    }
}
