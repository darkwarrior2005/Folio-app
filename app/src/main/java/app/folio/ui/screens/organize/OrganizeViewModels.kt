package app.folio.ui.screens.organize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.LibraryBook
import app.folio.core.model.LibraryQuery
import app.folio.core.model.SmartCollection
import app.folio.data.db.CategoryWithCount
import app.folio.data.db.CollectionEntity
import app.folio.data.db.CollectionWithCount
import app.folio.data.db.TagWithCount
import app.folio.ui.components.moved
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CollectionsViewModel(private val container: AppContainer) : ViewModel() {

    val collections: StateFlow<List<CollectionWithCount>> = container.organization.collections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<CategoryWithCount>> = container.organization.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<TagWithCount>> = container.organization.tags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val books = container.library.libraryBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Counts for the automatic collections, computed from the same rules the library filters use. */
    val smartCounts: StateFlow<Map<SmartCollection, Int>> = books
        .map { list ->
            val now = System.currentTimeMillis()
            SmartCollection.entries.associateWith { smart ->
                list.count { LibraryQuery.matchesSmart(it, smart, now) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun create(name: String) = viewModelScope.launch { container.organization.createCollection(name) }

    fun rename(collection: CollectionEntity, name: String) = viewModelScope.launch {
        container.organization.updateCollection(collection.copy(name = name.trim()))
    }

    fun delete(id: Long) = viewModelScope.launch { container.organization.deleteCollection(id) }

    fun createCategory(name: String) = viewModelScope.launch { container.organization.ensureCategory(name) }

    fun renameCategory(id: Long, name: String) = viewModelScope.launch {
        container.organization.renameCategory(id, name)
    }

    fun deleteCategory(id: Long) = viewModelScope.launch { container.organization.deleteCategory(id) }

    fun renameTag(id: Long, name: String) = viewModelScope.launch { container.organization.renameTag(id, name) }

    fun deleteTag(id: Long) = viewModelScope.launch { container.organization.deleteTag(id) }

    fun mergeTags(from: Long, into: Long) = viewModelScope.launch { container.organization.mergeTags(from, into) }
}

class CollectionDetailViewModel(
    private val container: AppContainer,
    private val collectionId: Long,
) : ViewModel() {

    val collection: StateFlow<CollectionEntity?> = container.organization.observeCollection(collectionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val books: StateFlow<List<LibraryBook>> =
        combine(container.organization.collectionLinks, container.library.libraryBooks) { links, library ->
            val byId = library.associateBy { it.id }
            links.filter { it.collectionId == collectionId }
                .sortedBy { it.position }
                .mapNotNull { byId[it.bookId] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun move(from: Int, to: Int) = viewModelScope.launch {
        val ordered = books.value.moved(from, to).map { it.id }
        container.organization.reorderCollection(collectionId, ordered)
    }

    fun remove(bookId: Long) = viewModelScope.launch {
        container.organization.removeFromCollection(collectionId, listOf(bookId))
    }

    fun rename(name: String) = viewModelScope.launch {
        val current = collection.value ?: return@launch
        container.organization.updateCollection(current.copy(name = name.trim()))
    }

    fun delete() = viewModelScope.launch { container.organization.deleteCollection(collectionId) }
}

class QueueViewModel(private val container: AppContainer) : ViewModel() {

    val books: StateFlow<List<LibraryBook>> =
        combine(container.organization.queue, container.library.libraryBooks) { queue, library ->
            val byId = library.associateBy { it.id }
            queue.sortedBy { it.position }.mapNotNull { byId[it.bookId] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun move(from: Int, to: Int) = viewModelScope.launch {
        container.organization.reorderQueue(books.value.moved(from, to).map { it.id })
    }

    fun remove(bookId: Long) = viewModelScope.launch { container.organization.dequeue(bookId) }
}

class TrashViewModel(private val container: AppContainer) : ViewModel() {

    val books: StateFlow<List<LibraryBook>> = container.library.removedBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val retentionDays: StateFlow<Int> = container.settings.settings
        .map { it.storage.trashRetentionDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 30)

    fun restore(bookId: Long) = viewModelScope.launch { container.library.restore(listOf(bookId)) }

    fun purge(bookId: Long) = viewModelScope.launch { container.library.purge(listOf(bookId)) }

    fun purgeAll() = viewModelScope.launch {
        container.library.purge(books.value.map { it.id })
    }
}
