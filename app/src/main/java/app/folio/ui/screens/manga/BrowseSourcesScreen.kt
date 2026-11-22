package app.folio.ui.screens.manga

import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.AppContainer
import app.folio.ui.folioViewModel
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Enabled sources, tap one to open [SourceCatalogueScreen] on it. */
@Composable
fun BrowseSourcesScreen(onOpenSource: (Long) -> Unit, onBack: () -> Unit) {
    val viewModel: BrowseSourcesViewModel = folioViewModel { BrowseSourcesViewModel(it) }
    val sources by viewModel.sources.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Browse") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(sources, key = { it.id }) { source ->
                ListItem(
                    headlineContent = { Text(source.name) },
                    supportingContent = { Text(source.lang.uppercase()) },
                    modifier = Modifier.clickable { onOpenSource(source.id) },
                )
            }
        }
    }
}

class BrowseSourcesViewModel(container: AppContainer) : ViewModel() {
    val sources: StateFlow<List<CatalogueSource>> = container.sourceManager.sources
        .map { map -> map.values.filterIsInstance<CatalogueSource>().sortedBy { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
