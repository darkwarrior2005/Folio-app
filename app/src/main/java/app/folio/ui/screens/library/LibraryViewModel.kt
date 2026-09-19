package app.folio.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.BookFormat
import app.folio.core.model.BookSort
import app.folio.core.model.LibraryBook
import app.folio.core.model.LibraryFilter
import app.folio.core.model.LibraryGrouping
import app.folio.core.model.LibraryLayout
import app.folio.core.model.LibraryQuery
import app.folio.core.model.ReadingStatus
import app.folio.core.model.SmartCollection
import app.folio.data.db.CategoryWithCount
import app.folio.data.db.CollectionWithCount
import app.folio.data.db.TagWithCount
import app.folio.data.settings.AppSettings
import app.folio.importer.ImportEvent
import app.folio.importer.ImportProgress
import app.folio.importer.PendingDuplicate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    private val _duplicate = MutableStateFlow<PendingDuplicate?>(null)
    val duplicate: StateFlow<PendingDuplicate?> = _duplicate.asStateFlow()

    val allBooks: StateFlow<List<LibraryBook>> = container.library.libraryBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val books: StateFlow<List<LibraryBook>> =
        combine(allBooks, _filter, settings) { books, filter, settings ->
            LibraryQuery.apply(
                books = books,
                filter = filter,
                sort = settings.library.sort,
                ascending = settings.library.sortAscending,
                now = System.currentTimeMillis(),
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val grouped: StateFlow<List<Pair<String?, List<LibraryBook>>>> =
        combine(books, settings) { books, settings ->
            LibraryQuery.group(books, settings.library.grouping)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryWithCount>> = container.organization.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<TagWithCount>> = container.organization.tags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<CollectionWithCount>> = container.organization.collections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val authors: StateFlow<List<String>> = allBooks
        .map { books -> books.mapNotNull { it.author }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val series: StateFlow<List<String>> = allBooks
        .map { books -> books.mapNotNull { it.series }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val importProgress: StateFlow<ImportProgress> = container.imports.progress

    init {
        viewModelScope.launch {
            container.imports.events.collect { event ->
                if (event is ImportEvent.Duplicate) _duplicate.value = event.pending
            }
        }
    }

    // ---- Filtering and sorting -------------------------------------------------

    fun setQuery(query: String) {
        _filter.value = _filter.value.copy(query = query)
    }

    fun toggleFormat(format: BookFormat) = update { it.copy(formats = it.formats.toggle(format)) }

    fun toggleCategory(id: Long) = update { it.copy(categoryIds = it.categoryIds.toggle(id)) }

    fun toggleTag(id: Long) = update { it.copy(tagIds = it.tagIds.toggle(id)) }

    fun toggleCollection(id: Long) = update { it.copy(collectionIds = it.collectionIds.toggle(id)) }

    fun toggleStatus(status: ReadingStatus) = update { it.copy(statuses = it.statuses.toggle(status)) }

    fun toggleAuthor(author: String) = update { it.copy(authors = it.authors.toggle(author)) }

    fun toggleSeries(series: String) = update { it.copy(series = it.series.toggle(series)) }

    fun toggleFavorites() = update { it.copy(favoritesOnly = !it.favoritesOnly) }

    fun setSmart(smart: SmartCollection?) = update { it.copy(smart = if (it.smart == smart) null else smart) }

    fun clearFilters() {
        _filter.value = LibraryFilter(query = _filter.value.query)
    }

    private fun update(transform: (LibraryFilter) -> LibraryFilter) {
        _filter.value = transform(_filter.value)
    }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (contains(value)) this - value else this + value

    fun setSort(sort: BookSort) = viewModelScope.launch {
        container.settings.update { current ->
            val ascending = if (current.library.sort == sort) !current.library.sortAscending else defaultAscending(sort)
            current.copy(library = current.library.copy(sort = sort, sortAscending = ascending))
        }
    }

    private fun defaultAscending(sort: BookSort) = when (sort) {
        BookSort.TITLE, BookSort.AUTHOR, BookSort.SERIES, BookSort.CUSTOM -> true
        else -> false
    }

    fun setLayout(layout: LibraryLayout) = viewModelScope.launch {
        container.settings.update { it.copy(library = it.library.copy(layout = layout)) }
    }

    fun setGrouping(grouping: LibraryGrouping) = viewModelScope.launch {
        container.settings.update { it.copy(library = it.library.copy(grouping = grouping)) }
    }

    // ---- Selection and bulk actions --------------------------------------------

    fun toggleSelected(id: Long) {
        _selection.value = _selection.value.toggle(id)
    }

    fun selectAll() {
        _selection.value = books.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    private fun selectedIds() = _selection.value.toList()

    fun setFavoriteForSelection(favorite: Boolean) = viewModelScope.launch {
        container.library.setFavorite(selectedIds(), favorite)
        clearSelection()
    }

    fun setStatusForSelection(status: ReadingStatus) = viewModelScope.launch {
        container.library.setStatus(selectedIds(), status)
        clearSelection()
    }

    fun setCategoryForSelection(categoryId: Long?) = viewModelScope.launch {
        container.library.setCategory(selectedIds(), categoryId)
        clearSelection()
    }

    fun addTagToSelection(name: String) = viewModelScope.launch {
        container.organization.addTagsToBooks(selectedIds(), listOf(name))
        clearSelection()
    }

    fun removeTagFromSelection(tagId: Long) = viewModelScope.launch {
        container.organization.removeTagFromBooks(tagId, selectedIds())
        clearSelection()
    }

    fun addSelectionToCollection(collectionId: Long) = viewModelScope.launch {
        container.organization.addToCollection(collectionId, selectedIds())
        clearSelection()
    }

    fun removeSelectionFromCollection(collectionId: Long) = viewModelScope.launch {
        container.organization.removeFromCollection(collectionId, selectedIds())
        clearSelection()
    }

    fun removeSelectionFromLibrary() = viewModelScope.launch {
        container.library.removeFromLibrary(selectedIds())
        clearSelection()
    }

    fun queueSelection() = viewModelScope.launch {
        selectedIds().forEach { container.organization.enqueue(it) }
        clearSelection()
    }

    // ---- Single book actions ---------------------------------------------------

    fun toggleFavorite(book: LibraryBook) = viewModelScope.launch {
        container.library.setFavorite(listOf(book.id), !book.favorite)
    }

    fun setStatus(bookId: Long, status: ReadingStatus) = viewModelScope.launch {
        container.library.setStatus(listOf(bookId), status)
    }

    fun removeFromLibrary(bookId: Long) = viewModelScope.launch {
        container.library.removeFromLibrary(listOf(bookId))
    }

    fun enqueue(bookId: Long) = viewModelScope.launch { container.organization.enqueue(bookId) }

    fun addTagToBook(bookId: Long, name: String) = viewModelScope.launch {
        container.organization.addTagsToBooks(listOf(bookId), listOf(name))
    }

    fun addToCollection(collectionId: Long, bookId: Long) = viewModelScope.launch {
        container.organization.addToCollection(collectionId, listOf(bookId))
    }

    fun createCollectionWith(name: String, bookIds: List<Long>) = viewModelScope.launch {
        val id = container.organization.createCollection(name)
        if (bookIds.isNotEmpty()) container.organization.addToCollection(id, bookIds)
        clearSelection()
    }

    fun createCategoryAndAssign(name: String, bookIds: List<Long>) = viewModelScope.launch {
        val id = container.organization.ensureCategory(name) ?: return@launch
        container.library.setCategory(bookIds, id)
        clearSelection()
    }

    // ---- Import ----------------------------------------------------------------

    fun import(uris: List<String>) = container.imports.enqueue(uris)

    fun importFolder(treeUri: String) = container.imports.enqueueFolder(treeUri)

    fun resolveDuplicate(action: app.folio.importer.DuplicateAction) {
        val pending = _duplicate.value ?: return
        container.imports.resolveDuplicate(pending.token, action)
        _duplicate.value = null
    }

    fun dismissDuplicate() {
        _duplicate.value = null
    }
}
