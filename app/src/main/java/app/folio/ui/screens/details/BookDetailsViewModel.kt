package app.folio.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.LibraryBook
import app.folio.core.model.ReadingStatus
import app.folio.data.db.BookEntity
import app.folio.data.db.BookmarkEntity
import app.folio.data.db.CollectionWithCount
import app.folio.data.db.HighlightEntity
import app.folio.data.db.NoteEntity
import app.folio.data.db.TagEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookReadingStats(
    val totalMs: Long = 0,
    val sessions: Int = 0,
    val pages: Int = 0,
    val lastRead: Long? = null,
)

class BookDetailsViewModel(
    private val container: AppContainer,
    private val bookId: Long,
) : ViewModel() {

    val book: StateFlow<LibraryBook?> = container.library.observeBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entity: StateFlow<BookEntity?> = container.library.observeEntity(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val tags: StateFlow<List<TagEntity>> = container.organization.tagsForBook(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<CollectionWithCount>> = container.organization.collections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val memberCollectionIds: StateFlow<Set<Long>> = container.organization.collectionLinks
        .map { links -> links.filter { it.bookId == bookId }.map { it.collectionId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val bookmarks: StateFlow<List<BookmarkEntity>> = container.annotations.bookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val highlights: StateFlow<List<HighlightEntity>> = container.annotations.highlights(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = container.annotations.notes(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<BookReadingStats> = container.reading.sessionsForBook(bookId)
        .map { sessions ->
            BookReadingStats(
                totalMs = sessions.sumOf { it.durationMs },
                sessions = sessions.size,
                pages = sessions.sumOf { it.pagesRead },
                lastRead = sessions.maxOfOrNull { it.endedAt },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookReadingStats())

    val queued: StateFlow<Boolean> = container.organization.queue
        .map { queue -> queue.any { it.bookId == bookId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _available = MutableStateFlow(true)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    init {
        viewModelScope.launch { _available.value = container.library.isAvailable(bookId) }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun toggleFavorite() = viewModelScope.launch {
        val current = book.value ?: return@launch
        container.library.setFavorite(listOf(bookId), !current.favorite)
    }

    fun setStatus(status: ReadingStatus) = viewModelScope.launch {
        container.library.setStatus(listOf(bookId), status)
    }

    fun addToCollection(collectionId: Long) = viewModelScope.launch {
        container.organization.addToCollection(collectionId, listOf(bookId))
    }

    fun removeFromCollection(collectionId: Long) = viewModelScope.launch {
        container.organization.removeFromCollection(collectionId, listOf(bookId))
    }

    fun createCollection(name: String) = viewModelScope.launch {
        val id = container.organization.createCollection(name)
        container.organization.addToCollection(id, listOf(bookId))
    }

    fun toggleQueue() = viewModelScope.launch {
        if (queued.value) container.organization.dequeue(bookId) else container.organization.enqueue(bookId)
    }

    fun removeFromLibrary() = viewModelScope.launch {
        container.library.removeFromLibrary(listOf(bookId))
    }

    fun deleteFile(onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(container.library.deleteFileAndForget(bookId))
    }

    fun renameFile(newName: String, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        onDone(container.library.renamePhysicalFile(bookId, newName))
    }

    /** The user pointed us at the file again after it moved. */
    fun relocate(uri: String) = viewModelScope.launch {
        val info = container.files.info(uri) ?: return@launch
        container.files.takePersistablePermission(container.files.uriOf(uri))
        val hash = container.files.openInput(uri)?.use {
            app.folio.data.files.Hashing.quickHash(it, info.size)
        } ?: return@launch
        container.library.relocate(bookId, uri, info.name, info.size, hash)
        _available.value = true
    }

    fun rescanMetadata() = viewModelScope.launch {
        val current = container.library.entity(bookId) ?: return@launch
        container.rescanner.rescan(current)
    }

    fun setCoverFromImage(uri: String) = viewModelScope.launch {
        val bitmap = container.files.openInput(uri)?.use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: return@launch
        container.library.setCustomCover(bookId, bitmap)
    }

    fun removeCover() = viewModelScope.launch { container.library.hideCover(bookId) }

    fun restoreCover() = viewModelScope.launch { container.library.restoreOriginalCover(bookId) }

    fun exportNotes(targetUri: String) = viewModelScope.launch {
        container.backup.exportAnnotations(targetUri, bookId)
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch { container.annotations.deleteBookmark(id) }

    fun deleteHighlight(id: Long) = viewModelScope.launch { container.annotations.deleteHighlight(id) }

    fun deleteNote(id: Long) = viewModelScope.launch { container.annotations.deleteNote(id) }
}
