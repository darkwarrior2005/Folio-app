package app.folio.manga.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

private const val EXTENSION_FEATURE = "tachiyomi.extension"
private const val METADATA_SOURCE_CLASS = "tachiyomi.extension.class"
private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
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
        // Extensions carry their lib version in versionName: "1.6.1" is lib 1.6, extension version 1.
        val versionName = packageInfo.versionName.orEmpty()
        val libVersion = versionName.substringBeforeLast('.').toDoubleOrNull()
            ?: return ExtensionLoadResult.Failed(packageName, "No extension-lib version in \"$versionName\"")
        if (libVersion !in SUPPORTED_LIB_VERSIONS) {
            return ExtensionLoadResult.Failed(packageName, "Unsupported extension-lib version $libVersion")
        }
        val classNames = metaData.getString(METADATA_SOURCE_CLASS)
            ?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.map { if (it.startsWith(".")) packageName + it else it }
            .orEmpty()
        if (classNames.isEmpty()) return ExtensionLoadResult.Failed(packageName, "No source class declared")

        return try {
            val classLoader = ExtensionClassLoader(appInfo.sourceDir, context.classLoader)
            val sources = classNames.flatMap { className ->
                when (val obj = Class.forName(className, false, classLoader).getDeclaredConstructor().newInstance()) {
                    is SourceFactory -> obj.createSources()
                    is Source -> listOf(obj)
                    else -> return ExtensionLoadResult.Failed(packageName, "Unknown source class type")
                }
            }
            ExtensionLoadResult.Loaded(
                LoadedExtension(
                    packageName = packageName,
                    name = pmLabel(packageInfo).removePrefix("Tachiyomi: "),
                    versionName = versionName,
                    versionCode = longVersionCode(packageInfo),
                    libVersion = libVersion,
                    sources = sources,
                    isNsfw = metaData.getInt(METADATA_NSFW, 0) == 1,
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
