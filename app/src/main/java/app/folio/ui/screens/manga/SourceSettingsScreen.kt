package app.folio.ui.screens.manga

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.core.os.bundleOf
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import app.folio.FolioApp
import app.folio.R
import app.folio.data.settings.AppSettings
import app.folio.ui.LocalContainer
import app.folio.ui.components.EmptyState
import app.folio.ui.icons.FolioIcons
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.preferenceKey
import kotlinx.coroutines.launch

/**
 * A source's own settings: language, image quality, mirror and the like, laid out by the
 * extension itself through [ConfigurableSource.setupPreferenceScreen], exactly as Mihon shows
 * them. Values land in the same `source_<id>` preferences the extension reads.
 *
 * Extensions often read their settings once, when created, so leaving this screen after a change
 * reloads the extensions for the new values to take effect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSettingsScreen(sourceId: Long, onBack: () -> Unit) {
    val container = LocalContainer.current
    val source = remember(sourceId) { container.sources.get(sourceId) }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    DisposableEffect(sourceId) {
        onDispose {
            if (SourcePreferencesFragment.consumeChanged(sourceId)) {
                container.appScope.launch { container.extensions.refreshInstalled() }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(source?.toString() ?: stringResource(R.string.manga_source_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FolioIcons.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (source !is ConfigurableSource) {
            EmptyState(
                title = stringResource(R.string.manga_source_settings),
                message = stringResource(R.string.manga_source_settings_none),
                icon = FolioIcons.Settings,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        AndroidFragment<SourcePreferencesFragment>(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            arguments = bundleOf(
                SourcePreferencesFragment.SOURCE_ID to sourceId,
                SourcePreferencesFragment.DARK to dark,
                SourcePreferencesFragment.INCOGNITO to settings.privacy.incognitoKeyboard,
            ),
        )
    }
}

/** Hosts the extension's preference screen. Adapted from Mihon (Apache-2.0). */
class SourcePreferencesFragment : PreferenceFragmentCompat() {

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        changedSources += requireArguments().getLong(SOURCE_ID)
    }

    /**
     * Folio picks light or dark itself rather than following the system, so the preference views
     * get the matching overlay; otherwise their text could be dark on Folio's dark background.
     */
    override fun getContext(): Context? {
        val base = super.getContext() ?: return null
        val dark = arguments?.getBoolean(DARK) ?: false
        return ContextThemeWrapper(
            base,
            if (dark) R.style.ThemeOverlay_Folio_SourcePrefs_Dark else R.style.ThemeOverlay_Folio_SourcePrefs_Light,
        )
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val sourceId = requireArguments().getLong(SOURCE_ID)
        val container = (requireActivity().application as FolioApp).container
        val source = container.sources.get(sourceId) as? ConfigurableSource
        val incognito = requireArguments().getBoolean(INCOGNITO, true)

        // The file the extension reads its settings from (getSourcePreferences()).
        if (source != null) preferenceManager.sharedPreferencesName = source.preferenceKey()
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        if (source != null) {
            runCatching { source.setupPreferenceScreen(screen) }
            screen.forEachPreference { pref ->
                pref.isIconSpaceReserved = false
                pref.isSingleLineTitle = false
                if (pref is DialogPreference && pref.dialogTitle.isNullOrEmpty()) pref.dialogTitle = pref.title
                if (pref is EditTextPreference && incognito) {
                    // Chained after the extension's own (e.g. a numbers-only field). If it cannot be
                    // read, the extension's is left as it is rather than replaced.
                    pref.existingBindListener()?.let { existing ->
                        pref.setOnBindEditTextListener { editText ->
                            existing.listener?.onBindEditText(editText)
                            editText.imeOptions = editText.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                        }
                    }
                }
            }
        }
        preferenceScreen = screen
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }

    /** The listener an extension set, which the library keeps package-private. */
    private class BindListener(val listener: EditTextPreference.OnBindEditTextListener?)

    private fun EditTextPreference.existingBindListener(): BindListener? = runCatching {
        val getter = EditTextPreference::class.java.getDeclaredMethod("getOnBindEditTextListener")
        getter.isAccessible = true
        BindListener(getter.invoke(this) as? EditTextPreference.OnBindEditTextListener)
    }.getOrNull()

    private fun PreferenceGroup.forEachPreference(action: (Preference) -> Unit) {
        for (index in 0 until preferenceCount) {
            val pref = getPreference(index)
            action(pref)
            if (pref is PreferenceGroup) pref.forEachPreference(action)
        }
    }

    companion object {
        const val SOURCE_ID = "source_id"
        const val DARK = "dark"
        const val INCOGNITO = "incognito"

        private val changedSources = mutableSetOf<Long>()

        /** Whether [sourceId]'s settings changed since the last call. */
        fun consumeChanged(sourceId: Long): Boolean = changedSources.remove(sourceId)
    }
}
