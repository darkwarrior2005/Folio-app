package app.folio.manga.extension

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.net.toUri
import app.folio.manga.MangaAccess
import app.folio.manga.extension.model.Extension
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Downloads an extension apk and hands it to the system installer.
 *
 * The system asks the user to confirm every install, which is the point: Folio does not install
 * code silently, and the confirmation names the package being added.
 *
 * Adapted from Mihon (Apache-2.0), which also supports installing through Shizuku and into its
 * own private directory. This uses only the system package installer.
 */
class ExtensionInstaller(
    private val context: Context,
    private val network: NetworkHelper,
    private val access: MangaAccess,
    private val repos: ExtensionRepoService,
) {

    /**
     * Fetches the apk and starts the install. The user then sees the system's confirmation
     * dialog; the result of the install arrives at [ExtensionInstallReceiver], not here, so
     * success here only means the apk was downloaded and handed over.
     *
     * The apk's signature is not checked at this point. It is checked by [ExtensionLoader]
     * before any of the extension's code runs, against the signing key of the repository that
     * offered it, so an apk swapped in transit is downloaded but never loaded.
     */
    suspend fun install(extension: Extension.Available): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            access.require()

            val repo = repos.repos().firstOrNull { it.baseUrl == extension.repoUrl }
                ?: error("The repository offering ${extension.name} is no longer added")
            check(repo.signingKeyFingerprint.isNotBlank()) {
                "${repo.name} publishes no signing key, so its extensions cannot be trusted"
            }

            val apk = downloadApk(extension)
            try {
                commitSession(apk, extension.pkgName)
            } finally {
                // The session has copied the bytes by the time commit returns.
                apk.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun downloadApk(extension: Extension.Available): File {
        val target = File(cacheDir(), "${extension.pkgName}-${extension.versionCode}.apk")
        val response = network.client.newCall(GET(extension.apkUrl)).awaitSuccess()
        response.body.byteStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    private fun commitSession(apk: File, pkgName: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(pkgName) }

        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(WRITE_NAME, 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            session.commit(statusIntent(sessionId).intentSender)
        }
    }

    private fun statusIntent(sessionId: Int): PendingIntent {
        val intent = Intent(context, ExtensionInstallReceiver::class.java)
            .setAction(ExtensionInstallReceiver.ACTION_INSTALL_STATUS)
        // The system fills in the status extras, so the intent has to stay mutable.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
    }

    /** Starts the system's uninstall flow. The user confirms it there. */
    fun uninstall(pkgName: String) {
        val intent = Intent(Intent.ACTION_DELETE)
            .setData("package:$pkgName".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun cacheDir(): File = File(context.cacheDir, "extensions").apply { mkdirs() }

    private companion object {
        const val WRITE_NAME = "extension.apk"
    }
}
