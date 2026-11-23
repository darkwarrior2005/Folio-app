package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.MangaFilter
import app.folio.core.model.MangaSort
import app.folio.core.model.SourceLanguages
import app.folio.data.db.MangaWithCounts
import app.folio.manga.source.GlobalSearch
import app.folio.manga.source.SourceSearchResult
import app.folio.manga.source.SourceSearchState
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A search of the installed sources: what was searched, and how far each source got. */
data class SourceSearch(val query: String, val sourceCount: Int, val results: List<SourceSearchResult>)

/** An installed source, as the "choose sources" sheet lists it. */
data class SearchableSource(val id: Long, val name: String, val lang: String, val included: Boolean)

class MangaLibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(MangaSort.LAST_READ)
    val sort: StateFlow<MangaSort> = _sort.asStateFlow()

    private val _filter = MutableStateFlow(MangaFilter.ALL)
    val filter: StateFlow<MangaFilter> = _filter.asStateFlow()

    val sourcesEnabled: StateFlow<Boolean> = container.mangaAccess.observeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val queueSize: StateFlow<Int> = container.downloads.observeQueueSize()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val manga: StateFlow<List<MangaWithCounts>> =
        combine(container.manga.observeLibrary(), _query, _sort, _filter) { library, query, sort, filter ->
            library
                .filter { it.matches(query) && it.matches(filter) }
                .sortedWith(comparatorFor(sort))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val globalSearch = GlobalSearch()
    private var searchJob: Job? = null

    /** Sources searched again on their own; the main search no longer speaks for them. */
    private val retryJobs = mutableMapOf<Long, Job>()

    private val _sourceSearch = MutableStateFlow<SourceSearch?>(null)

    /** Null until the user searches their sources; cleared with the query. */
    val sourceSearch: StateFlow<SourceSearch?> = _sourceSearch.asStateFlow()

    val hiddenLanguages: StateFlow<Set<String>> = container.observeHiddenLanguages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Sources in a language the user hid are left out entirely, not just switched off. */
    val searchableSources: StateFlow<List<SearchableSource>> = combine(
        container.sources.catalogueSources,
        container.settings.settings.map { it.manga.globalSearchExcludedSources },
        hiddenLanguages,
    ) { sources, excluded, hidden ->
        sources.filter { SourceLanguages.isShown(it.lang, hidden) }
            .map { SearchableSource(it.id, it.name, it.lang, included = it.id !in excluded) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The languages of the installed sources, for the language picker. */
    val languageChoices: StateFlow<List<String>> = combine(container.sources.catalogueSources, hiddenLanguages) { sources, hidden ->
        SourceLanguages.choices(sources.map { it.lang }, hidden)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setHiddenLanguages(hidden: Set<String>) {
        viewModelScope.launch { container.setHiddenLanguages(hidden) }
    }

    init {
        // So the downloaded-count badge agrees with what a chapter open actually finds on disk.
        viewModelScope.launch { container.downloads.healMissingFiles() }
    }

    fun setQuery(value: String) {
        _query.value = value
        if (value.isBlank()) clearSourceSearch()
    }

    /**
     * Searches every installed source the user has not turned off. Runs on submit rather than
     * on every keystroke: each letter would otherwise send a request to every source.
     */
    fun searchSources() {
        val query = _query.value.trim()
        if (query.isEmpty()) return
        cancelSearches()
        searchJob = viewModelScope.launch {
            val manga = container.settings.current().manga
            val targets = container.sources.catalogueSources.first().filter {
                it.id !in manga.globalSearchExcludedSources && SourceLanguages.isShown(it.lang, manga.hiddenLanguages)
            }
            _sourceSearch.value = SourceSearch(query, targets.size, emptyList())
            globalSearch.search(targets, query).collect { results ->
                _sourceSearch.update { current ->
                    val retried = current?.results.orEmpty().filter { it.sourceId in retryJobs }.associateBy { it.sourceId }
                    SourceSearch(query, targets.size, results.map { retried[it.sourceId] ?: it })
                }
            }
        }
    }

    /** Searches one source again, such as after the user passed its site's check. */
    fun retrySource(sourceId: Long) {
        val query = _sourceSearch.value?.query ?: return
        val source = container.sources.getCatalogue(sourceId) ?: return
        retryJobs.remove(sourceId)?.cancel()
        setSourceState(sourceId, SourceSearchState.Loading)
        retryJobs[sourceId] = viewModelScope.launch {
            setSourceState(sourceId, globalSearch.searchOne(source, query))
        }
    }

    private fun setSourceState(sourceId: Long, state: SourceSearchState) {
        _sourceSearch.update { current ->
            current?.copy(results = current.results.map { if (it.sourceId == sourceId) it.copy(state = state) else it })
        }
    }

    fun clearSourceSearch() {
        cancelSearches()
        _sourceSearch.value = null
    }

    private fun cancelSearches() {
        searchJob?.cancel()
        searchJob = null
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
    }

    fun setSourcesIncluded(sourceIds: Collection<Long>, included: Boolean) {
        viewModelScope.launch {
            container.settings.update { settings ->
                val excluded = settings.manga.globalSearchExcludedSources
                val updated = if (included) excluded - sourceIds.toSet() else excluded + sourceIds
                settings.copy(manga = settings.manga.copy(globalSearchExcludedSources = updated))
            }
        }
    }

    /**
     * Stores a search result so it has an ID to open with. It is stored unfavourited, so looking
     * at something does not add it to the library.
     */
    fun openSourceManga(sourceId: Long, manga: SManga, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            onReady(container.manga.findOrCreate(sourceId, manga, favorite = false).id)
        }
    }

    fun setSort(value: MangaSort) {
        _sort.value = value
    }

    fun setFilter(value: MangaFilter) {
        _filter.value = value
    }

    /** Takes a series out of the library. Downloads are deleted with it. */
    fun remove(mangaId: Long) {
        viewModelScope.launch {
            container.downloads.deleteAllFor(mangaId)
            container.manga.remove(mangaId)
        }
    }

    private fun MangaWithCounts.matches(query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim()
        return manga.title.contains(needle, ignoreCase = true) ||
            manga.author?.contains(needle, ignoreCase = true) == true
    }

    private fun MangaWithCounts.matches(filter: MangaFilter): Boolean = when (filter) {
        MangaFilter.ALL -> true
        MangaFilter.UNREAD -> unreadCount > 0
        MangaFilter.DOWNLOADED -> downloadedCount > 0
        MangaFilter.STARTED -> chapterCount > unreadCount && unreadCount > 0
    }

    private fun comparatorFor(sort: MangaSort): Comparator<MangaWithCounts> = when (sort) {
        MangaSort.TITLE -> compareBy { it.manga.title.lowercase() }
        // Never read sorts last rather than first.
        MangaSort.LAST_READ -> compareByDescending { it.manga.lastReadAt ?: Long.MIN_VALUE }
        MangaSort.DATE_ADDED -> compareByDescending { it.manga.dateAdded }
        MangaSort.UNREAD_COUNT -> compareByDescending { it.unreadCount }
        MangaSort.LAST_UPDATED -> compareByDescending { it.manga.lastChapterFetchAt ?: Long.MIN_VALUE }
    }
}
