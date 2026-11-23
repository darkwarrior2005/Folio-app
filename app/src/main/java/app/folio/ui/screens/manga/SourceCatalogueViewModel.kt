package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.manga.source.needsWebView
import app.folio.manga.source.sourceErrorMessage
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which listing of a source is being shown. */
enum class CatalogueMode { POPULAR, LATEST, SEARCH }

data class CatalogueState(
    val sourceName: String = "",
    val supportsLatest: Boolean = false,
    val mode: CatalogueMode = CatalogueMode.POPULAR,
    val items: List<SManga> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasNextPage: Boolean = false,
    val error: String? = null,
    /** The error is one the user can get past by opening the site and passing its check. */
    val needsWebView: Boolean = false,
)

class SourceCatalogueViewModel(
    private val container: AppContainer,
    private val sourceId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(CatalogueState())
    val state: StateFlow<CatalogueState> = _state.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private var page = 1
    private var loadJob: Job? = null

    private val source: CatalogueSource? get() = container.sources.getCatalogue(sourceId)

    /** Whether the source has settings of its own to show. */
    val hasSettings: Boolean get() = source is ConfigurableSource

    init {
        val current = source
        _state.update {
            it.copy(
                sourceName = current?.name ?: "",
                supportsLatest = current?.supportsLatest == true,
            )
        }
        load(CatalogueMode.POPULAR)
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun search() {
        load(CatalogueMode.SEARCH)
    }

    fun load(mode: CatalogueMode) {
        loadJob?.cancel()
        page = 1
        _state.update { it.copy(mode = mode, items = emptyList(), loading = true, error = null, needsWebView = false) }
        loadJob = viewModelScope.launch { fetch(mode, append = false) }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasNextPage) return
        page += 1
        _state.update { it.copy(loadingMore = true) }
        loadJob = viewModelScope.launch { fetch(current.mode, append = true) }
    }

    fun retry() {
        load(_state.value.mode)
    }

    private suspend fun fetch(mode: CatalogueMode, append: Boolean) {
        val current = source
        if (current == null) {
            _state.update {
                it.copy(loading = false, loadingMore = false, error = container.sources.unavailableMessage(sourceId))
            }
            return
        }

        try {
            val result = withContext(Dispatchers.IO) {
                when (mode) {
                    CatalogueMode.POPULAR -> current.getPopularManga(page)
                    CatalogueMode.LATEST -> current.getLatestUpdates(page)
                    CatalogueMode.SEARCH -> current.getSearchManga(page, _query.value, FilterList())
                }
            }
            _state.update {
                it.copy(
                    items = if (append) it.items + result.mangas else result.mangas,
                    loading = false,
                    loadingMore = false,
                    hasNextPage = result.hasNextPage,
                    error = null,
                    needsWebView = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e, append)
        } catch (e: LinkageError) {
            // An extension built against API this app lacks; reported, not allowed to crash.
            fail(e, append)
        }
    }

    private fun fail(error: Throwable, append: Boolean) {
        // A page that failed is not added, so loadMore can be retried.
        if (append) page -= 1
        _state.update {
            it.copy(
                loading = false,
                loadingMore = false,
                error = error.sourceErrorMessage(),
                needsWebView = error.needsWebView(),
            )
        }
    }

    /**
     * Stores the manga so it has an ID to navigate with. Browsing stores it unfavourited, so
     * looking at something does not add it to the library.
     */
    fun openManga(manga: SManga, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            val entity = container.manga.findOrCreate(sourceId, manga, favorite = false)
            onReady(entity.id)
        }
    }
}
