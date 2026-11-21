package app.folio.ui.share

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Sends the selected text to the device's dictionary/translation apps via
 * ACTION_PROCESS_TEXT, the same intent a text field's "Look up" would use.
 */
fun lookUp(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
    }
    val resolved = context.packageManager.queryIntentActivities(intent, 0)
    if (resolved.isEmpty()) {
        Toast.makeText(context, "No dictionary or translation app found", Toast.LENGTH_SHORT).show()
        return
    }
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
