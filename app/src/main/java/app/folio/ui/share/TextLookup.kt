package app.folio.ui.share

import android.content.Context
import android.content.Intent
import android.widget.Toast
import app.folio.R

/**
 * Looks a word or passage up in the dictionary and translation apps already on the phone (the
 * ones that offer "Define" or "Translate" on selected text). Folio sends nothing anywhere itself:
 * the chosen app decides what happens next.
 */
object TextLookup {

    fun lookUp(context: Context, text: String) {
        val query = text.trim().take(500)
        if (query.isEmpty()) return
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, query)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        }
        val apps = context.packageManager.queryIntentActivities(intent, 0)
        if (apps.isEmpty()) {
            Toast.makeText(context, R.string.lookup_no_app, Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.lookup_title)))
        }
    }
}
