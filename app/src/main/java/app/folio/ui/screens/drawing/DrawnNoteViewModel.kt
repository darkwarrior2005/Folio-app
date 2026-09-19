package app.folio.ui.screens.drawing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.InkPoint
import app.folio.core.model.InkStroke
import app.folio.core.model.InkTool
import app.folio.core.model.InkToolStyle
import app.folio.data.db.DrawnNoteEntity
import app.folio.data.db.toInkStroke
import app.folio.reader.api.ScribbleState
import app.folio.ui.screens.reader.InkSession
import app.folio.ui.screens.reader.UndoRedo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DrawnNoteViewModel(private val container: AppContainer, private val noteId: Long) : ViewModel() {

    val note: StateFlow<DrawnNoteEntity?> = container.ink.observeDrawnNote(noteId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val bookTitle: StateFlow<String?> = note.filterNotNull()
        .map { container.library.entity(it.bookId)?.displayTitle }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val strokes: StateFlow<List<InkStroke>> = container.ink.noteStrokes(noteId)
        .map { list -> list.map { it.toInkStroke() } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val scribble: StateFlow<ScribbleState> = container.settings.settings
        .map { ScribbleState(active = true, tool = it.scribble.lastTool, style = it.scribble.styleFor(it.scribble.lastTool)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScribbleState(active = true))

    // App scope: strokes and the empty-note clean-up must finish after the screen closes.
    private val session = InkSession(container.ink, container.appScope)
    val undoRedo: StateFlow<UndoRedo> = session.undoRedo

    fun stroke(points: List<InkPoint>, heightOverWidth: Float) {
        val current = note.value ?: return
        val state = scribble.value
        session.addStroke(current.bookId, null, noteId, state.tool, state.style, points, heightOverWidth)
    }

    fun erase(point: InkPoint, radius: Float, heightOverWidth: Float) =
        session.erase(strokes.value, point, radius, heightOverWidth)

    fun endErase() = session.endErase()

    fun undo() = session.undo()

    fun redo() = session.redo()

    fun selectTool(tool: InkTool) {
        viewModelScope.launch { container.settings.update { it.copy(scribble = it.scribble.copy(lastTool = tool)) } }
    }

    fun updateStyle(style: InkToolStyle) {
        val tool = scribble.value.tool
        viewModelScope.launch { container.settings.update { it.copy(scribble = it.scribble.withStyle(tool, style)) } }
    }

    fun delete() {
        session.afterPendingWrites { container.ink.deleteDrawnNote(noteId) }
    }

    override fun onCleared() {
        session.afterPendingWrites { container.ink.discardIfEmpty(noteId) }
        super.onCleared()
    }
}
