package app.folio.manga.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import app.folio.data.files.Hashing.toHex
import app.folio.manga.extension.model.ContentWarning
import app.folio.manga.extension.model.Extension
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Finds the manga extensions installed on the device and turns the trusted ones into sources.
 *
 * An extension is an ordinary apk that declares the `tachiyomi.extension` feature and names its
 * source classes in its manifest metadata, which is why extensions written for Tachiyomi and
 * Mihon work here unchanged.
 *
 * Adapted from Mihon (Apache-2.0). Mihon also supports private extensions installed into its own
 * data directory and installation through Shizuku; this handles only ordinary installed packages.
 */
class ExtensionLoader(
    private val context: Context,
    private val trust: ExtensionTrust,
) {

    /**
     * Loads every installed extension. Returns one entry per extension, loaded or not, so the
     * UI can explain the ones that were refused instead of hiding them.
     */
    suspend fun loadAll(
        allowedContentWarnings: Set<ContentWarning> = ContentWarning.entries.toSet(),
    ): List<Extension.Installed> = withContext(Dispatchers.IO) {
        installedExtensionPackages().map { pkgInfo ->
            loadCatching(pkgInfo, allowedContentWarnings)
        }
    }

    private fun installedExtensionPackages(): List<PackageInfo> {
        val pkgManager = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pkgManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pkgManager.getInstalledPackages(PACKAGE_FLAGS)
        }
        return packages.filter { isExtension(it) }
    }

    /**
     * An apk can be broken in ways the checks below do not name. Keeping the failure attached to
     * the extension that caused it means one bad extension cannot stop the others from loading.
     */
    private suspend fun loadCatching(
        pkgInfo: PackageInfo,
        allowedContentWarnings: Set<ContentWarning>,
    ): Extension.Installed = try {
        doLoad(pkgInfo, allowedContentWarnings)
    } catch (e: Throwable) {
        Extension.NotLoaded(
            name = pkgInfo.packageName,
            pkgName = pkgInfo.packageName,
            versionName = pkgInfo.versionName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
            libVersion = null,
            lang = "",
            contentWarning = ContentWarning.SAFE,
            signatures = signaturesOf(pkgInfo).orEmpty(),
            reason = Extension.NotLoaded.Reason.Failed(e.rootMessage()),
        )
    }

    private suspend fun doLoad(
        pkgInfo: PackageInfo,
        allowedContentWarnings: Set<ContentWarning>,
    ): Extension.Installed {
        val pkgManager = context.packageManager
        val appInfo = pkgInfo.applicationInfo
        val metaData = appInfo?.metaData
        val pkgName = pkgInfo.packageName
        val versionName = pkgInfo.versionName
        val versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo)
        val signatures = signaturesOf(pkgInfo).orEmpty()

        val extName = metaData?.getString(METADATA_NAME)
            ?: appInfo?.let { pkgManager.getApplicationLabel(it).toString().substringAfter("Tachiyomi: ") }
            ?: pkgName

        val contentWarning = when {
            metaData == null -> ContentWarning.SAFE
            metaData.containsKey(METADATA_CONTENT_WARNING) -> when (metaData.getInt(METADATA_CONTENT_WARNING)) {
                1 -> ContentWarning.MIXED
                2 -> ContentWarning.NSFW
                else -> ContentWarning.SAFE
            }
            metaData.getInt(METADATA_NSFW) == 1 -> ContentWarning.NSFW
            else -> ContentWarning.SAFE
        }

        fun notLoaded(reason: Extension.NotLoaded.Reason, libVersion: Double? = null) = Extension.NotLoaded(
            name = extName,
            pkgName = pkgName,
            versionName = versionName.orEmpty(),
            versionCode = versionCode,
            libVersion = libVersion,
            lang = "",
            contentWarning = contentWarning,
            signatures = signatures,
            reason = reason,
        )

        if (appInfo == null || metaData == null || versionName.isNullOrEmpty()) {
            return notLoaded(Extension.NotLoaded.Reason.Malformed)
        }

        // Either stated outright, or taken from the version name: 1.4.x means lib 1.4.
        val libVersion = metaData.getFloat(METADATA_EXTENSION_LIB)
            .takeUnless { it == 0.0f }
            ?.toString()
            ?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull()

        if (libVersion == null || libVersion !in SUPPORTED_LIB_VERSIONS) {
            return notLoaded(Extension.NotLoaded.Reason.UnsupportedLibVersion, libVersion)
        }

        if (signatures.isEmpty()) {
            return notLoaded(Extension.NotLoaded.Reason.Unsigned, libVersion)
        }

        // Nothing below this point may run before the user has accepted this apk: creating the
        // class loader and instantiating a source class both execute the extension's own code.
        if (!trust.isTrusted(pkgInfo, signatures)) {
            return notLoaded(Extension.NotLoaded.Reason.Untrusted(signatures.last()), libVersion)
        }

        if (contentWarning !in allowedContentWarnings) {
            return notLoaded(Extension.NotLoaded.Reason.Filtered, libVersion)
        }

        val classLoader = try {
            extensionClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
        } catch (e: Exception) {
            return notLoaded(Extension.NotLoaded.Reason.Failed(e.rootMessage()), libVersion)
        }

        val sourceClasses = metaData.getString(METADATA_SOURCE_CLASS)
        if (sourceClasses.isNullOrBlank()) {
            return notLoaded(Extension.NotLoaded.Reason.Malformed, libVersion)
        }

        val sources = sourceClasses
            .split(";")
            .map { declared ->
                val sourceClass = declared.trim()
                // A leading dot means the class sits in the extension's own package.
                if (sourceClass.startsWith(".")) pkgName + sourceClass else sourceClass
            }
            .flatMap { className ->
                try {
                    when (val instance = Class.forName(className, false, classLoader)
                        .getDeclaredConstructor()
                        .newInstance()
                    ) {
                        is Source -> listOf(instance)
                        is SourceFactory -> instance.createSources()
                        else -> return notLoaded(
                            Extension.NotLoaded.Reason.Failed(
                                "Unknown source class type: ${instance.javaClass.name}",
                            ),
                            libVersion,
                        )
                    }
                } catch (e: Throwable) {
                    return notLoaded(Extension.NotLoaded.Reason.Failed(e.rootMessage()), libVersion)
                }
            }

        val langs = sources.map { it.lang }.toSet()
        val lang = when (langs.size) {
            0 -> ""
            1 -> langs.first()
            else -> "all"
        }

        return Extension.Loaded(
            name = extName,
            pkgName = pkgName,
            versionName = versionName,
            versionCode = versionCode,
            libVersion = libVersion,
            lang = lang,
            contentWarning = contentWarning,
            signatures = signatures,
            sources = sources,
            icon = runCatching { appInfo.loadIcon(pkgManager) }.getOrNull(),
        )
    }

    private fun isExtension(pkgInfo: PackageInfo): Boolean =
        pkgInfo.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }

    /** SHA-256 of each signing certificate, oldest first; null when the apk is unsigned. */
    private fun signaturesOf(pkgInfo: PackageInfo): List<String>? {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            when {
                signingInfo == null -> null
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures
        }
        return signatures?.map { sha256(it.toByteArray()) }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** The deepest cause carries the message that actually says what went wrong. */
    private fun Throwable.rootMessage(): String {
        val root = generateSequence(this) { it.cause }.last()
        return listOfNotNull(root::class.simpleName, root.message).joinToString(": ")
    }

    companion object {
        private const val EXTENSION_FEATURE = "tachiyomi.extension"
        private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
        private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        private const val METADATA_NAME = "tachiyomix.name"
        private const val METADATA_EXTENSION_LIB = "tachiyomix.extensionLib"
        private const val METADATA_CONTENT_WARNING = "tachiyomix.contentWarning"

        /** The extensions-lib versions this app implements. */
        private val SUPPORTED_LIB_VERSIONS = listOf(1.4, 1.6)

        @Suppress("DEPRECATION")
        private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)
    }
}
