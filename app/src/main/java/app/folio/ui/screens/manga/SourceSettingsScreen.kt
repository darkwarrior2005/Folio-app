package app.folio.ui.screens.manga

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * A source's own settings, normally [eu.kanade.tachiyomi.source.ConfigurableSource
 * .setupPreferenceScreen] rendered in a PreferenceFragmentCompat with the incognito keyboard
 * applied reflectively (EditTextPreference.getOnBindEditTextListener is package-private).
 *
 * That `PreferenceFragmentCompat` bridge is Fragment/View-based rather than Compose and is left
 * as a follow-up: wiring a Fragment host into this Compose nav graph needs its own pass. For now
 * this is a stub so the route exists and other screens can link to it.
 */
@Composable
fun SourceSettingsScreen(packageName: String, onBack: () -> Unit) {
    Scaffold(topBar = { app.folio.ui.components.BackTopBar("Source settings", onBack) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("$packageName has no settings screen here yet")
        }
    }
}
