package app.folio.manga.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Notices extension packages being added, replaced or removed outside the app's own install
 * flow (the Play Store's cousin, `adb install`, a file manager) so [ExtensionManager] stays in
 * sync.
 */
class ExtensionInstallReceiver : BroadcastReceiver() {

    var listener: Listener? = null

    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.data?.schemeSpecificPart ?: return
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED -> listener?.onExtensionChanged(packageName)
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    listener?.onExtensionRemoved(packageName)
                }
            }
        }
    }

    interface Listener {
        fun onExtensionChanged(packageName: String)
        fun onExtensionRemoved(packageName: String)
    }
}
