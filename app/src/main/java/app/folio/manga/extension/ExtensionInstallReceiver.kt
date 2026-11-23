package app.folio.manga.extension

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import app.folio.FolioApp
import kotlinx.coroutines.launch

/**
 * Receives the outcome of an extension install, and the system's request to show the user the
 * confirmation dialog.
 *
 * Also listens for packages being added, replaced or removed so the source list follows what is
 * actually installed, including extensions installed from outside Folio.
 */
class ExtensionInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_INSTALL_STATUS -> handleInstallStatus(context, intent)
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED,
            -> reloadExtensions(context)
        }
    }

    private fun handleInstallStatus(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // The system wants the user to confirm. Nothing installs until they do.
                @Suppress("DEPRECATION")
                val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                confirmation?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> reloadExtensions(context)
            else -> Unit // A failure message is shown by the system installer.
        }
    }

    private fun reloadExtensions(context: Context) {
        val container = (context.applicationContext as? FolioApp)?.container ?: return
        container.appScope.launch { container.extensions.refreshInstalled() }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "app.folio.EXTENSION_INSTALL_STATUS"
    }
}
