package app.folio.ui.screens.reader

import app.folio.core.model.InkPoint
import app.folio.core.model.InkStroke
import app.folio.core.model.InkStyle
import app.folio.core.model.InkTool
import app.folio.core.model.InkToolStyle
import app.folio.core.model.StrokeHit
import app.folio.core.model.UndoStack
import app.folio.data.db.InkStrokeEntity
import app.folio.data.repo.InkStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface InkEdit {
    class Added(val stroke: InkStrokeEntity) : InkEdit
    class Erased(val strokes: List<InkStrokeEntity>) : InkEdit
}

data class UndoRedo(val canUndo: Boolean = false, val canRedo: Boolean = false)

/**
 * One drawing session: writes happen one at a time in order, so undo never races the insert it
 * undoes. Order is guaranteed two ways: the mutex is fair (FIFO), and each write is launched
 * [CoroutineStart.UNDISPATCHED] so it reaches `mutex.lock()` synchronously, on the caller's
 * thread, before the call that requested it returns — otherwise, since [scope] runs on a
 * multi-threaded dispatcher, two dispatched-but-not-yet-started coroutines could reach the mutex
 * in a different order than they were requested in. Use an app-wide scope: a stroke finished just
 * before leaving must still save.
 */
class InkSession(
    private val ink: InkStore,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit = { android.util.Log.w("InkSession", "write failed", it) },
) {
    private val history = UndoStack<InkEdit>()
    private val mutex = Mutex()
    private val _undoRedo = MutableStateFlow(UndoRedo())
    val undoRedo: StateFlow<UndoRedo> = _undoRedo.asStateFlow()

    /** Strokes removed by the eraser gesture in progress; one gesture is one undo step. */
    private val erasing = mutableListOf<InkStrokeEntity>()
    private val erasingIds = mutableSetOf<Long>()

    private fun publish() {
        _undoRedo.value = UndoRedo(history.canUndo, history.canRedo)
    }

    // A SQLite failure (disk full, a foreign-key violation from undoing an erase whose drawn
    // note was already discarded, ...) must not crash the app: log it and keep the session usable
    // for the next write. Cancellation is rethrown so scope teardown still works normally.
    private fun serially(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    onError(e)
                }
            }
        }
    }

    fun addStroke(
        bookId: Long,
        page: Int?,
        drawnNoteId: Long?,
        tool: InkTool,
        style: InkToolStyle,
        points: List<InkPoint>,
        heightOverWidth: Float,
    ) = serially {
        val saved = ink.addStroke(
            bookId = bookId,
            page = page,
            drawnNoteId = drawnNoteId,
            tool = tool,
            argb = InkStyle.strokeArgb(style),
            widthNorm = InkStyle.widthNorm(tool, style.widthIndex),
            points = points,
            heightOverWidth = heightOverWidth,
        ) ?: return@serially
        history.push(InkEdit.Added(saved))
        publish()
    }

    fun erase(strokes: List<InkStroke>, point: InkPoint, radius: Float, heightOverWidth: Float) {
        val hit = strokes
            .filter { it.id !in erasingIds && StrokeHit.hits(it.points, it.widthNorm, point, radius, heightOverWidth) }
            .map { it.id }
        if (hit.isEmpty()) return
        erasingIds += hit
        serially {
            val removed = ink.strokes(hit)
            ink.deleteStrokes(hit)
            erasing += removed
        }
    }

    /** Called on the main thread when the eraser lifts; [erasingIds] is only touched there. */
    fun endErase() {
        erasingIds.clear()
        serially {
            if (erasing.isNotEmpty()) {
                history.push(InkEdit.Erased(erasing.toList()))
                publish()
            }
            erasing.clear()
        }
    }

    fun undo() = serially {
        when (val edit = history.undo() ?: return@serially) {
            is InkEdit.Added -> ink.deleteStrokes(listOf(edit.stroke.id))
            is InkEdit.Erased -> ink.restoreStrokes(edit.strokes)
        }
        publish()
    }

    fun redo() = serially {
        when (val edit = history.redo() ?: return@serially) {
            is InkEdit.Added -> ink.restoreStrokes(listOf(edit.stroke))
            is InkEdit.Erased -> ink.deleteStrokes(edit.strokes.map { it.id })
        }
        publish()
    }

    /** Runs [block] after every write already requested, e.g. discarding an empty drawn note. */
    fun afterPendingWrites(block: suspend () -> Unit) = serially(block)
}
