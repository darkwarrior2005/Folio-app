package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.Manga
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MangaLibraryViewModel(private val container: AppContainer) : ViewModel() {

    val library: StateFlow<List<Manga>> = container.manga.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun sourceName(sourceId: Long): String =
        container.sourceManager.get(sourceId)?.name ?: "Unknown source"

    fun removeFromLibrary(mangaId: Long) {
        viewModelScope.launch { container.manga.setFavorite(mangaId, false) }
    }
}
