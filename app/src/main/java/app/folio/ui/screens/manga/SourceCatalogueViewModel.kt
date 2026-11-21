package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CatalogueMode { POPULAR, LATEST, SEARCH }

data class CatalogueState(
    val manga: List<SManga> = emptyList(),
    val page: Int = 1,
    val hasNextPage: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
    val mode: CatalogueMode = CatalogueMode.POPULAR,
    val query: String = "",
)

class SourceCatalogueViewModel(private val container: AppContainer, private val sourceId: Long) : ViewModel() {

    private val source: HttpSource? = container.sourceManager.get(sourceId) as? HttpSource

    private val _state = MutableStateFlow(CatalogueState())
    val state: StateFlow<CatalogueState> = _state

    init {
        loadMore()
    }

    fun search(query: String) {
        _state.value = CatalogueState(mode = CatalogueMode.SEARCH, query = query)
        loadMore()
    }

    fun switchMode(mode: CatalogueMode) {
        _state.value = CatalogueState(mode = mode)
        loadMore()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || !current.hasNextPage) return
        val src = source ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                container.mangaAccess.requireEnabled()
                val result = when (current.mode) {
                    CatalogueMode.POPULAR -> src.getPopularManga(current.page)
                    CatalogueMode.LATEST -> src.getLatestUpdates(current.page)
                    CatalogueMode.SEARCH -> src.getSearchManga(current.page, current.query, src.getFilterList())
                }
                _state.update {
                    it.copy(
                        manga = it.manga + result.mangas,
                        page = it.page + 1,
                        hasNextPage = result.hasNextPage,
                        loading = false,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Failed to load") }
            }
        }
    }

    suspend fun addToLibrary(manga: SManga): Long {
        val added = container.manga.findOrCreate(sourceId, manga)
        container.manga.setFavorite(added.id, true)
        return added.id
    }
}
