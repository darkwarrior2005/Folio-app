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
        val fingerprint = fingerprintOf(packageInfo) ?: return false
        val versionCode = longVersionCodeOf(packageInfo)
        return mangaDao.trustFor(packageInfo.packageName)
            .any { it.versionCode == versionCode && it.signatureHash == fingerprint }
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

    /** True when [fingerprint] matches a signing key a trusted repository is known to use. */
    suspend fun isRepoSignedKey(fingerprint: String, repoFingerprints: Collection<String>): Boolean =
        fingerprint in repoFingerprints

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
