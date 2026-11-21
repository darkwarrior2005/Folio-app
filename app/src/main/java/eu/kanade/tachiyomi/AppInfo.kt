package eu.kanade.tachiyomi

import android.content.Context
import android.content.pm.PackageManager

/**
 * A handful of extensions (MangaDex among them) probe this at runtime and crash if it is absent.
 * Mirrors the shape Tachiyomi/Mihon ships so those extensions' version checks succeed.
 */
object AppInfo {

    @JvmStatic
    fun getVersionName(): String = versionName

    @JvmStatic
    fun getVersionCode(): Long = versionCode

    private var versionName: String = "0.0.0"
    private var versionCode: Long = 0

    fun init(context: Context) {
        val packageInfo = try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
        versionName = packageInfo?.versionName ?: versionName
        versionCode = packageInfo?.let {
            @Suppress("DEPRECATION")
            it.longVersionCode
        } ?: versionCode
    }
}
