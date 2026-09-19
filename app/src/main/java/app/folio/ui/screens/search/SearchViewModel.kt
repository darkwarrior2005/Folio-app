package app.folio.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.LibraryBook
import app.folio.core.model.LibraryQuery
import app.folio.data.repo.FullTextResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AnnotationHit(
    val bookId: Long,
    val bookTitle: String,
    val text: String,
    val label: String,
    val location: String,
    val kind: Kind,
) {
    enum class Kind { BOOKMARK, HIGHLIGHT, NOTE }
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _bookResults = MutableStateFlow<List<LibraryBook>>(emptyList())
    val bookResults: StateFlow<List<LibraryBook>> = _bookResults.asStateFlow()

    private val _textResults = MutableStateFlow<List<FullTextResult>>(emptyList())
    val textResults: StateFlow<List<FullTextResult>> = _textResults.asStateFlow()

    private val _annotationResults = MutableStateFlow<List<AnnotationHit>>(emptyList())
    val annotationResults: StateFlow<List<AnnotationHit>> = _annotationResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val books = container.library.libraryBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val titles: StateFlow<Map<Long, String>> = books
        .map { list -> list.associate { book -> book.id to book.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            _query.debounce(220).collect { value -> runSearch(value) }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    private suspend fun runSearch(value: String) {
        val trimmed = value.trim()
        if (trimmed.length < 2) {
            _bookResults.value = emptyList()
            _textResults.value = emptyList()
            _annotationResults.value = emptyList()
            return
        }
        _searching.value = true

        _bookResults.value = books.value.filter { LibraryQuery.matchesText(it, trimmed) }.take(50)
        _textResults.value = container.search.searchEverywhere(trimmed)

        val lowered = trimmed.lowercase()
        val titleById = books.value.associate { it.id to it.title }
        val hits = mutableListOf<AnnotationHit>()
        container.annotations.allHighlightsOnce().forEach { highlight ->
            if (highlight.text.contains(lowered, true) || highlight.note?.contains(lowered, true) == true) {
                hits += AnnotationHit(
                    bookId = highlight.bookId,
                    bookTitle = titleById[highlight.bookId].orEmpty(),
                    text = highlight.note?.takeIf { it.contains(lowered, true) } ?: highlight.text,
                    label = highlight.positionLabel,
                    location = highlight.location,
                    kind = AnnotationHit.Kind.HIGHLIGHT,
                )
            }
        }
        container.annotations.allNotesOnce().forEach { note ->
            if (note.text.contains(lowered, true)) {
                hits += AnnotationHit(
                    bookId = note.bookId,
                    bookTitle = titleById[note.bookId].orEmpty(),
                    text = note.text,
                    label = note.positionLabel,
                    location = note.location,
                    kind = AnnotationHit.Kind.NOTE,
                )
            }
        }
        container.annotations.allBookmarksOnce().forEach { bookmark ->
            val text = bookmark.title ?: bookmark.note
            if (text != null && text.contains(lowered, true)) {
                hits += AnnotationHit(
                    bookId = bookmark.bookId,
                    bookTitle = titleById[bookmark.bookId].orEmpty(),
                    text = text,
                    label = bookmark.positionLabel,
                    location = bookmark.location,
                    kind = AnnotationHit.Kind.BOOKMARK,
                )
            }
        }
        _annotationResults.value = hits.take(80)
        _searching.value = false
    }
}
