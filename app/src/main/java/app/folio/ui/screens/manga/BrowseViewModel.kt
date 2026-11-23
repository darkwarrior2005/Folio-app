package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.SourceLanguages
import eu.kanade.tachiyomi.source.CatalogueSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The sources the user can browse right now. */
class BrowseViewModel(private val container: AppContainer) : ViewModel() {

    val hiddenLanguages: StateFlow<Set<String>> = container.observeHiddenLanguages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Installed sources in the languages the user kept. */
    val sources: StateFlow<List<CatalogueSource>> = combine(container.sources.catalogueSources, hiddenLanguages) { sources, hidden ->
        sources.filter { SourceLanguages.isShown(it.lang, hidden) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val languageChoices: StateFlow<List<String>> = combine(container.sources.catalogueSources, hiddenLanguages) { sources, hidden ->
        SourceLanguages.choices(sources.map { it.lang }, hidden)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setHiddenLanguages(hidden: Set<String>) {
        viewModelScope.launch { container.setHiddenLanguages(hidden) }
    }

    val sourcesEnabled: StateFlow<Boolean> = container.mangaAccess.observeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}
