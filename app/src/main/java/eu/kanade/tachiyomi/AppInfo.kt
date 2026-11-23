package eu.kanade.tachiyomi

import android.os.Build
import app.folio.BuildConfig

/**
 * Facts about the app, for extensions (MangaDex sends the version in its User-Agent, for one).
 * Part of the extensions-lib API, so the name, package and signatures must stay as they are.
 *
 * Adapted from Mihon (Apache-2.0).
 */
@Suppress("UNUSED")
object AppInfo {

    /** @since extension-lib 1.3 */
    fun getVersionCode(): Int = BuildConfig.VERSION_CODE

    /** @since extension-lib 1.3 */
    fun getVersionName(): String = BuildConfig.VERSION_NAME

    /**
     * The image types the reader can decode, so a source can ask the site for one of them.
     *
     * @since extension-lib 1.5
     */
    fun getSupportedImageMimeTypes(): List<String> = buildList {
        addAll(listOf("image/jpeg", "image/png", "image/webp", "image/gif"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add("image/heif")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add("image/avif")
    }
}
