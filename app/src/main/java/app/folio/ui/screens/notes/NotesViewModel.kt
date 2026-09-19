package app.folio.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.HighlightColor
import app.folio.core.model.InkStroke
import app.folio.data.db.BookmarkWithBook
import app.folio.data.db.DrawnNoteWithBook
import app.folio.data.db.HighlightWithBook
import app.folio.data.db.NoteWithBook
import app.folio.data.db.toInkStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrawingEntry(val drawing: DrawnNoteWithBook, val strokes: List<InkStroke>)

class NotesViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _colorFilter = MutableStateFlow<HighlightColor?>(null)
    val colorFilter: StateFlow<HighlightColor?> = _colorFilter.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkWithBook>> =
        combine(container.annotations.allBookmarks, _query) { list, query ->
            if (query.isBlank()) {
                list
            } else {
                list.filter { entry ->
                    entry.bookTitle.contains(query, true) ||
                        entry.bookmark.title?.contains(query, true) == true ||
                        entry.bookmark.note?.contains(query, true) == true
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val highlights: StateFlow<List<HighlightWithBook>> =
        combine(container.annotations.allHighlights, _query, _colorFilter) { list, query, color ->
            list.filter { entry ->
                (color == null || entry.highlight.color == color) &&
                    (
                        query.isBlank() ||
                            entry.bookTitle.contains(query, true) ||
                            entry.highlight.text.contains(query, true) ||
                            entry.highlight.note?.contains(query, true) == true
                        )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val notes: StateFlow<List<NoteWithBook>> =
        combine(container.annotations.allNotes, _query) { list, query ->
            if (query.isBlank()) {
                list
            } else {
                list.filter { entry ->
                    entry.bookTitle.contains(query, true) || entry.note.text.contains(query, true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val drawings: StateFlow<List<DrawingEntry>> =
        combine(container.ink.allDrawnNotes, container.ink.allNoteStrokes, _query) { drawings, strokes, query ->
            val byNote = strokes.groupBy { it.drawnNoteId }
            drawings
                .filter { entry ->
                    query.isBlank() ||
                        entry.bookTitle.contains(query, true) ||
                        entry.note.positionLabel.contains(query, true)
                }
                .map { entry -> DrawingEntry(entry, byNote[entry.note.id].orEmpty().map { it.toInkStroke() }) }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteDrawing(id: Long) = viewModelScope.launch { container.ink.deleteDrawnNote(id) }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun toggleColor(color: HighlightColor) {
        _colorFilter.value = if (_colorFilter.value == color) null else color
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch { container.annotations.deleteBookmark(id) }

    fun deleteHighlight(id: Long) = viewModelScope.launch { container.annotations.deleteHighlight(id) }

    fun deleteNote(id: Long) = viewModelScope.launch { container.annotations.deleteNote(id) }

    fun exportAll(targetUri: String) = viewModelScope.launch {
        container.backup.exportAnnotations(targetUri, null)
    }
}
