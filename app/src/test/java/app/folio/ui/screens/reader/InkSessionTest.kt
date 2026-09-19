package app.folio.ui.screens.reader

import app.folio.core.model.InkPacking
import app.folio.core.model.InkPoint
import app.folio.core.model.InkStyle
import app.folio.core.model.InkTool
import app.folio.data.db.InkStrokeEntity
import app.folio.data.db.toInkStroke
import app.folio.data.repo.InkStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * An in-memory [InkStore] whose [addStroke] suspends before writing, the way Room would. Run on
 * [Dispatchers.Default] (a real multi-threaded pool), this is what would let two `serially` calls
 * launched with a plain (dispatched) `scope.launch` reach the session's mutex out of call order:
 * the first call's coroutine can still be waiting for a free worker thread when the second one's
 * gets to run. `InkSession` now launches each write [kotlinx.coroutines.CoroutineStart.UNDISPATCHED],
 * which reaches `mutex.lock()` synchronously before the call returns, so ordering no longer depends
 * on how the pool happens to schedule the two coroutines.
 */
private class FakeInkStore(private val addDelayMs: Long = 5) : InkStore {
    private val byId = LinkedHashMap<Long, InkStrokeEntity>()
    private val nextId = AtomicLong(1)

    override suspend fun addStroke(
        bookId: Long,
        page: Int?,
        drawnNoteId: Long?,
        tool: InkTool,
        argb: Int,
        widthNorm: Float,
        points: List<InkPoint>,
        heightOverWidth: Float,
    ): InkStrokeEntity? {
        delay(addDelayMs)
        val entity = InkStrokeEntity(
            id = 0,
            bookId = bookId,
            page = page,
            drawnNoteId = drawnNoteId,
            tool = tool,
            argb = argb,
            widthNorm = widthNorm,
            points = InkPacking.pack(points),
            createdAt = 0,
        ).withId(nextId.getAndIncrement())
        synchronized(byId) { byId[entity.id] = entity }
        return entity
    }

    override suspend fun strokes(ids: List<Long>): List<InkStrokeEntity> =
        synchronized(byId) { ids.mapNotNull { byId[it] } }

    override suspend fun deleteStrokes(ids: List<Long>) {
        synchronized(byId) { ids.forEach { byId.remove(it) } }
    }

    override suspend fun restoreStrokes(strokes: List<InkStrokeEntity>) {
        synchronized(byId) { strokes.forEach { byId[it.id] = it } }
    }

    fun snapshot(): List<InkStrokeEntity> = synchronized(byId) { byId.values.toList() }
}

/** Delegates to [delegate] but throws on the first [addStroke] call, then behaves normally. */
private class ThrowOnceInkStore(private val delegate: InkStore) : InkStore by delegate {
    private var thrown = false

    override suspend fun addStroke(
        bookId: Long,
        page: Int?,
        drawnNoteId: Long?,
        tool: InkTool,
        argb: Int,
        widthNorm: Float,
        points: List<InkPoint>,
        heightOverWidth: Float,
    ): InkStrokeEntity? {
        if (!thrown) {
            thrown = true
            throw IllegalStateException("disk full")
        }
        return delegate.addStroke(bookId, page, drawnNoteId, tool, argb, widthNorm, points, heightOverWidth)
    }
}

class InkSessionTest {

    private val point = InkPoint(0.5f, 0.5f)

    /** Waits for every write requested so far to finish, however many threads it ran on. */
    private suspend fun awaitPending(session: InkSession) {
        val done = CompletableDeferred<Unit>()
        session.afterPendingWrites { done.complete(Unit) }
        done.await()
    }

    @Test
    fun addStrokeThenImmediateUndoLeavesStoreEmpty() = runBlocking {
        val store = FakeInkStore()
        val scope = CoroutineScope(Dispatchers.Default)
        val session = InkSession(store, scope)
        try {
            session.addStroke(1, 0, null, InkTool.PEN, InkStyle.PEN_DEFAULT, listOf(point, point), 1f)
            session.undo()
            awaitPending(session)

            assertTrue(store.snapshot().isEmpty())
            assertFalse(session.undoRedo.value.canUndo)
            assertTrue(session.undoRedo.value.canRedo)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun redoRestoresTheSameId() = runBlocking {
        val store = FakeInkStore()
        val scope = CoroutineScope(Dispatchers.Default)
        val session = InkSession(store, scope)
        try {
            session.addStroke(1, 0, null, InkTool.PEN, InkStyle.PEN_DEFAULT, listOf(point, point), 1f)
            awaitPending(session)
            val idBefore = store.snapshot().single().id

            session.undo()
            session.redo()
            awaitPending(session)

            assertEquals(listOf(idBefore), store.snapshot().map { it.id })
            assertTrue(session.undoRedo.value.canUndo)
            assertFalse(session.undoRedo.value.canRedo)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun twoEraseGesturesThenEndEraseIsOneUndoStep() = runBlocking {
        val store = FakeInkStore()
        val scope = CoroutineScope(Dispatchers.Default)
        val session = InkSession(store, scope)
        try {
            session.addStroke(1, 0, null, InkTool.PEN, InkStyle.PEN_DEFAULT, listOf(InkPoint(0.2f, 0.2f)), 1f)
            session.addStroke(1, 0, null, InkTool.PEN, InkStyle.PEN_DEFAULT, listOf(InkPoint(0.8f, 0.8f)), 1f)
            awaitPending(session)
            val (strokeA, strokeB) = store.snapshot().sortedBy { it.id }

            // Two separate touches of the eraser gesture, each hitting a different stroke.
            session.erase(listOf(strokeA.toInkStroke()), InkPoint(0.2f, 0.2f), 0.01f, 1f)
            session.erase(listOf(strokeB.toInkStroke()), InkPoint(0.8f, 0.8f), 0.01f, 1f)
            session.endErase()
            awaitPending(session)

            assertTrue(store.snapshot().isEmpty())
            assertTrue(session.undoRedo.value.canUndo)
            assertFalse(session.undoRedo.value.canRedo)

            session.undo()
            awaitPending(session)

            // Undoing the erase restores both strokes; the two prior "add" edits are still on
            // the stack, so canUndo stays true (only the just-undone erase can be redone).
            assertEquals(setOf(strokeA.id, strokeB.id), store.snapshot().map { it.id }.toSet())
            assertTrue(session.undoRedo.value.canUndo)
            assertTrue(session.undoRedo.value.canRedo)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun newStrokeAfterUndoClearsRedo() = runBlocking {
        val store = FakeInkStore()
        val scope = CoroutineScope(Dispatchers.Default)
        val session = InkSession(store, scope)
        try {
            session.addStroke(1, 0, null, InkTool.PEN, InkStyle.PEN_DEFAULT, listOf(point, point), 1f)
            awaitPending(session)
            session.undo()
            awaitPending(session)
            assertTrue(session.undoRedo.value.canRedo)

            session.addStroke(1, 0, null, InkTool.PEN, InkStyle.PEN_DEFAULT, listOf(point, point), 1f)
            awaitPending(session)

            assertFalse(session.undoRedo.value.canRedo)
            assertTrue(session.undoRedo.value.canUndo)
        } finally {
            scope.cancel()
        }
    }

    /**
     * A store failure (disk full, a foreign-key violation from undoing an erase whose drawn note
     * was already discarded, ...) must not crash the session: the write is dropped, logged through
     * [InkSession]'s injectable error hook, and the session keeps accepting writes afterwards.
     */
    @Test
    fun writeFailureIsSwallowedAndLaterWriteSucceeds() = runBlocking {
        val delegate = FakeInkStore()
        val store = ThrowOnceInkStore(delegate)
        val scope = CoroutineScope(Dispatchers.Default)
        var caught: Throwable? = null
        val session = InkSession(store, scope, onError = { caught = it })
        try {
            session.addStroke(1, 0, null, InkTool.PEN, InkStyle.PEN_DEFAULT, listOf(point, point), 1f)
            awaitPending(session)

            assertTrue(delegate.snapshot().isEmpty())
            assertTrue(caught is IllegalStateException)
            assertFalse(session.undoRedo.value.canUndo)

            // The session must still work: a later write is not blocked by the earlier failure.
            session.addStroke(1, 0, null, InkTool.PEN, InkStyle.PEN_DEFAULT, listOf(point, point), 1f)
            awaitPending(session)

            assertEquals(1, delegate.snapshot().size)
            assertTrue(session.undoRedo.value.canUndo)
        } finally {
            scope.cancel()
        }
    }
}
