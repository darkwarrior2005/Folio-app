package app.folio.manga.extension.model

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.source.Source

/** What a source is likely to show. Sources are filtered on it before they are offered. */
enum class ContentWarning { SAFE, MIXED, NSFW }

/**
 * A manga extension: an apk, installed or offered by a repository, that contributes sources.
 *
 * Adapted from Mihon (Apache-2.0).
 */
sealed interface Extension {

    val name: String
    val pkgName: String
    val versionName: String
    val versionCode: Long
    val libVersion: Double?
    val lang: String
    val contentWarning: ContentWarning

    /** An extension that is on the device, whether or not its sources could be created. */
    sealed interface Installed : Extension {
        /** SHA-256 of each signing certificate. */
        val signatures: List<String>
    }

    /** Installed, trusted and running: its sources are registered. */
    data class Loaded(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double?,
        override val lang: String,
        override val contentWarning: ContentWarning,
        override val signatures: List<String>,
        val sources: List<Source>,
        val icon: Drawable? = null,
        val hasUpdate: Boolean = false,
    ) : Installed

    /**
     * Installed but not running. [reason] says why, and is what the UI turns into an explanation
     * and, for [Reason.Untrusted], a prompt.
     */
    data class NotLoaded(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double?,
        override val lang: String,
        override val contentWarning: ContentWarning,
        override val signatures: List<String>,
        val reason: Reason,
    ) : Installed {

        sealed interface Reason {
            /** The apk is missing metadata the loader needs. */
            data object Malformed : Reason

            /** Built against an extensions-lib version this app does not implement. */
            data object UnsupportedLibVersion : Reason

            /** No signing certificate at all. Never loaded. */
            data object Unsigned : Reason

            /** Signed by a key the user has not accepted. [signature] is what they would accept. */
            data class Untrusted(val signature: String) : Reason

            /** Its content warning is not among the ones the user allowed. */
            data object Filtered : Reason

            /** Anything else, with the underlying message kept for the UI. */
            data class Failed(val message: String) : Reason
        }
    }

    /** Offered by a repository and not installed, or installed at an older version. */
    data class Available(
        override val name: String,
        override val pkgName: String,
        override val versionName: String,
        override val versionCode: Long,
        override val libVersion: Double?,
        override val lang: String,
        override val contentWarning: ContentWarning,
        val apkUrl: String,
        val iconUrl: String,
        /** The repository that offers it, by its base URL. */
        val repoUrl: String,
        /** SHA-256 of the key that repository signs its extensions with. */
        val repoSigningKey: String,
        val sources: List<AvailableSource>,
    ) : Extension

    data class AvailableSource(
        val id: Long,
        val lang: String,
        val name: String,
        val baseUrl: String,
    )
}
