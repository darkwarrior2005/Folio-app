package eu.kanade.tachiyomi.network

import android.content.Context
import app.cash.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a snippet of JavaScript for a source, typically to decode a site's obfuscated page list.
 * Part of the extensions-lib API, so the name, package and signatures must stay as they are.
 *
 * Adapted from Mihon (Apache-2.0).
 */
@Suppress("UNUSED", "UNCHECKED_CAST")
class JavaScriptEngine(context: Context) {

    /**
     * Evaluates [script] and returns its result as a primitive (String, Int, ...).
     *
     * @since extensions-lib 1.4
     */
    suspend fun <T> evaluate(script: String): T = withContext(Dispatchers.Default) {
        QuickJs.create().use { it.evaluate(script) as T }
    }
}
