package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a source's own JavaScript in QuickJS. Extensions obtain this through Injekt and call this
 * exact signature (extensions-lib 1.4), so keep it as is.
 */
@Suppress("UNUSED", "UNCHECKED_CAST", "UNUSED_PARAMETER")
class JavaScriptEngine(context: Context) {

    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Default) {
        QuickJs.create().use {
            it.evaluate(script) as T
        }
    }
}
