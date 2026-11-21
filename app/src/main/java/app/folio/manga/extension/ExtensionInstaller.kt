package app.folio.manga.extension

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.injectLazy
import java.io.File

sealed class InstallStep {
    object Downloading : InstallStep()
    object Installing : InstallStep()
    object Success : InstallStep()
    data class Error(val message: String) : InstallStep()
}

const val EXTENSION_INSTALL_STATUS_ACTION = "app.folio.EXTENSION_INSTALL_STATUS"
const val EXTRA_PACKAGE_NAME = "app.folio.extra.PACKAGE_NAME"

/** Downloads an extension APK and hands it to the system's own package installer. */
class ExtensionInstaller(private val context: Context) {

    private val network: NetworkHelper by injectLazy()

    fun install(extension: AvailableExtension): Flow<InstallStep> = callbackFlow {
        trySend(InstallStep.Downloading)
        val apkFile = withContext(Dispatchers.IO) {
            val response = network.client.newCall(GET(extension.apkUrl)).awaitSuccess()
            File(context.cacheDir, "${extension.packageName}.apk").also { it.writeBytes(response.body.bytes()) }
        }

        trySend(InstallStep.Installing)
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                if (intent.getStringExtra(EXTRA_PACKAGE_NAME) != extension.packageName) return
                when (status) {
                    PackageInstaller.STATUS_SUCCESS -> trySend(InstallStep.Success)
                    else -> trySend(
                        InstallStep.Error(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed"),
                    )
                }
                close()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter(EXTENSION_INSTALL_STATUS_ACTION),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apkFile.inputStream().use { input ->
                session.openWrite("extension", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callbackIntent = Intent(EXTENSION_INSTALL_STATUS_ACTION).apply {
                putExtra(EXTRA_PACKAGE_NAME, extension.packageName)
                setPackage(context.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                callbackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pendingIntent.intentSender)
        }

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun uninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$packageName"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
