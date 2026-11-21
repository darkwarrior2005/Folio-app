package app.folio.manga.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

private const val EXTENSION_FEATURE = "tachiyomi.extension"
private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
private const val METADATA_LIB_VERSION = "tachiyomi.extension.lib.version"
private val SUPPORTED_LIB_VERSIONS = setOf(1.4, 1.5, 1.6)

data class LoadedExtension(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double,
    val sources: List<Source>,
    val isNsfw: Boolean,
)

sealed class ExtensionLoadResult {
    data class Loaded(val extension: LoadedExtension) : ExtensionLoadResult()
    data class Untrusted(val packageInfo: PackageInfo) : ExtensionLoadResult()
    data class Failed(val packageName: String, val reason: String) : ExtensionLoadResult()
}

/** Finds installed extension APKs and turns each into [Source] instances. */
class ExtensionLoader(private val context: Context, private val trust: ExtensionTrust) {

    @Suppress("DEPRECATION")
    fun installedExtensionPackages(): List<PackageInfo> {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA
        return pm.getInstalledPackages(flags).filter { pkg ->
            pkg.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE } ||
                pkg.applicationInfo?.metaData?.containsKey(METADATA_SOURCE_CLASS) == true
        }
    }

    suspend fun load(packageInfo: PackageInfo): ExtensionLoadResult {
        val packageName = packageInfo.packageName
        if (!trust.isTrusted(packageInfo)) {
            return ExtensionLoadResult.Untrusted(packageInfo)
        }

        val appInfo = packageInfo.applicationInfo
            ?: return ExtensionLoadResult.Failed(packageName, "No application info")
        val metaData = appInfo.metaData ?: return ExtensionLoadResult.Failed(packageName, "No metadata")
        val libVersion = metaData.getString(METADATA_LIB_VERSION)?.toDoubleOrNull()
            ?: return ExtensionLoadResult.Failed(packageName, "No extension-lib version declared")
        if (libVersion !in SUPPORTED_LIB_VERSIONS) {
            return ExtensionLoadResult.Failed(packageName, "Unsupported extension-lib version $libVersion")
        }
        val className = metaData.getString(METADATA_SOURCE_CLASS)
            ?: return ExtensionLoadResult.Failed(packageName, "No source class declared")
        val fullClassName = if (className.startsWith(".")) packageName + className else className

        return try {
            val classLoader = ExtensionClassLoader(appInfo.sourceDir, context.classLoader)
            val obj = Class.forName(fullClassName, false, classLoader).getDeclaredConstructor().newInstance()
            val sources = when (obj) {
                is SourceFactory -> obj.createSources()
                is Source -> listOf(obj)
                else -> return ExtensionLoadResult.Failed(packageName, "Unknown source class type")
            }
            ExtensionLoadResult.Loaded(
                LoadedExtension(
                    packageName = packageName,
                    name = pmLabel(packageInfo),
                    versionName = packageInfo.versionName ?: "",
                    versionCode = longVersionCode(packageInfo),
                    libVersion = libVersion,
                    sources = sources,
                    isNsfw = metaData.getInt("tachiyomi.extension.nsfw", 0) == 1,
                ),
            )
        } catch (e: Throwable) {
            // Extensions sometimes reference API this app doesn't have (a LinkageError); fail
            // that one extension instead of taking the app down.
            ExtensionLoadResult.Failed(packageName, e.message ?: e::class.simpleName.orEmpty())
        }
    }

    private fun pmLabel(packageInfo: PackageInfo): String =
        packageInfo.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: packageInfo.packageName

    @Suppress("DEPRECATION")
    private fun longVersionCode(packageInfo: PackageInfo): Long = packageInfo.longVersionCode
}
