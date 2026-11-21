package eu.kanade.tachiyomi.source

import androidx.preference.PreferenceScreen

/** A [Source] with its own settings screen, filled in from the host app's PreferenceScreen. */
interface ConfigurableSource : Source {

    fun setupPreferenceScreen(screen: PreferenceScreen)
}
