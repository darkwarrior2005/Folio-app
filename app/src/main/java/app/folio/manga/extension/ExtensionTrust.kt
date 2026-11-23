package app.folio.manga.extension

import android.content.pm.PackageInfo
import android.content.pm.Signature
import app.folio.data.db.MangaDao
import app.folio.data.db.TrustedExtensionEntity
import java.security.MessageDigest

/**
 * Trust is per package, version and signing key, so an update or a resigned APK must be accepted
 * again. A key that matches a repository the user already added is trusted automatically.
 */
class ExtensionTrust(private val mangaDao: MangaDao) {

    suspend fun isTrusted(packageInfo: PackageInfo): Boolean {
        // Signed with the key of a repository the user added: trusted, as in Mihon.
        val repoKeys = mangaDao.repos().mapNotNull { it.signingKeyFingerprint?.lowercase() }.toSet()
        if (repoKeys.isNotEmpty() && certificateFingerprintsOf(packageInfo).any { it in repoKeys }) return true

        val fingerprint = fingerprintOf(packageInfo) ?: return false
        val versionCode = longVersionCodeOf(packageInfo)
        return mangaDao.trustFor(packageInfo.packageName)
            .any { it.versionCode == versionCode && it.signatureHash == fingerprint }
    }

    /** SHA-256 of each signing certificate, the form repositories publish their key in. */
    fun certificateFingerprintsOf(packageInfo: PackageInfo): List<String> =
        signaturesOf(packageInfo).orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }

    suspend fun trust(packageInfo: PackageInfo) {
        val fingerprint = fingerprintOf(packageInfo) ?: return
        mangaDao.trust(
            TrustedExtensionEntity(
                packageName = packageInfo.packageName,
                versionCode = longVersionCodeOf(packageInfo),
                signatureHash = fingerprint,
            ),
        )
    }

    suspend fun revoke(packageName: String) = mangaDao.untrust(packageName)

    fun fingerprintOf(packageInfo: PackageInfo): String? {
        val signatures = signaturesOf(packageInfo) ?: return null
        if (signatures.isEmpty()) return null
        val digest = MessageDigest.getInstance("SHA-256")
        signatures.forEach { digest.update(it.toByteArray()) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun signaturesOf(packageInfo: PackageInfo): Array<Signature>? {
        val signingInfo = packageInfo.signingInfo
        return if (signingInfo != null) {
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            packageInfo.signatures
        }
    }

    @Suppress("DEPRECATION")
    private fun longVersionCodeOf(packageInfo: PackageInfo): Long = packageInfo.longVersionCode
}
