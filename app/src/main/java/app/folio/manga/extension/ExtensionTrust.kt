package app.folio.manga.extension

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import app.folio.data.db.FolioDatabase
import app.folio.data.db.TrustedExtensionEntity

/**
 * Which extensions the user has agreed to run.
 *
 * An extension is code from a stranger running inside Folio's own process, with Folio's
 * permissions and access to its files. Trust is therefore recorded per package *and* version
 * *and* signing key: an update, or an apk resigned by someone else, has to be accepted again
 * rather than inheriting the answer given to the version before it.
 *
 * An extension whose signing key matches a repository the user added is trusted without asking,
 * because adding that repository was the decision to trust its key.
 *
 * Adapted from Mihon (Apache-2.0).
 */
class ExtensionTrust(private val database: FolioDatabase) {

    private val dao get() = database.manga()

    suspend fun isTrusted(pkgInfo: PackageInfo, signatures: List<String>): Boolean {
        if (signatures.isEmpty()) return false

        val repoKeys = dao.repos().map { it.signingKeyFingerprint.lowercase() }
        if (signatures.any { it.lowercase() in repoKeys }) return true

        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        // The last signature is the current one; older ones are the rotation history.
        return dao.isTrusted(pkgInfo.packageName, versionCode, signatures.last())
    }

    /** Records the user's decision to run this exact apk. */
    suspend fun trust(pkgName: String, versionCode: Long, signature: String) {
        dao.trust(
            TrustedExtensionEntity(
                pkgName = pkgName,
                versionCode = versionCode,
                signature = signature,
                trustedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun revoke(pkgName: String) {
        dao.revokeTrust(pkgName)
    }

    /** Used when the user asks to be prompted for everything again. */
    suspend fun revokeAll() {
        dao.revokeAllTrust()
    }
}
