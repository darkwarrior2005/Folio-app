package app.folio.data.repo

import app.folio.core.model.BookLocation
import app.folio.core.model.InkPacking
import app.folio.core.model.InkPoint
import app.folio.core.model.InkStyle
import app.folio.core.model.InkTool
import app.folio.core.model.StrokeSimplifier
import app.folio.data.db.DrawnNoteEntity
import app.folio.data.db.DrawnNoteWithBook
import app.folio.data.db.FolioDatabase
import app.folio.data.db.InkDao
import app.folio.data.db.InkStrokeEntity
import app.folio.data.db.LocationCodec
import kotlinx.coroutines.flow.Flow

/**
 * The part of [InkRepository] that [app.folio.ui.screens.reader.InkSession] needs to write and
 * undo strokes, kept separate so a session can be driven by a fake store in tests.
 */
interface InkStore {
    suspend fun addStroke(
        bookId: Long,
        page: Int?,
        drawnNoteId: Long?,
        tool: InkTool,
        argb: Int,
        widthNorm: Float,
        points: List<InkPoint>,
        heightOverWidth: Float,
    ): InkStrokeEntity?

    suspend fun strokes(ids: List<Long>): List<InkStrokeEntity>

    suspend fun deleteStrokes(ids: List<Long>)

    /** Puts strokes back exactly as they were (same ids), for undo. */
    suspend fun restoreStrokes(strokes: List<InkStrokeEntity>)
}

class InkRepository(
    db: FolioDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : InkStore {
    private val dao: InkDao = db.ink()

    fun pageStrokes(bookId: Long): Flow<List<InkStrokeEntity>> = dao.observePageStrokes(bookId)

    fun noteStrokes(noteId: Long): Flow<List<InkStrokeEntity>> = dao.observeNoteStrokes(noteId)

    val allNoteStrokes: Flow<List<InkStrokeEntity>> = dao.observeAllNoteStrokes()

    /** Simplifies and saves a pen or highlighter stroke; returns it with its new id. */
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
        if (tool == InkTool.ERASER || points.isEmpty()) return null
        val simplified = StrokeSimplifier.simplify(points, heightOverWidth = heightOverWidth)
        val stroke = InkStrokeEntity(
            bookId = bookId,
            page = page,
            drawnNoteId = drawnNoteId,
            tool = tool,
            argb = argb,
            widthNorm = widthNorm,
            points = InkPacking.pack(simplified),
            createdAt = now(),
        )
        val id = dao.insertStroke(stroke)
        drawnNoteId?.let { dao.touchDrawnNote(it, now()) }
        return stroke.withId(id)
    }

    override suspend fun strokes(ids: List<Long>): List<InkStrokeEntity> = if (ids.isEmpty()) emptyList() else dao.strokes(ids)

    override suspend fun deleteStrokes(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.deleteStrokes(ids)
    }

    override suspend fun restoreStrokes(strokes: List<InkStrokeEntity>) {
        if (strokes.isNotEmpty()) dao.insertStrokes(strokes)
    }

    suspend fun createDrawnNote(bookId: Long, location: BookLocation, positionLabel: String, progress: Float): Long =
        dao.insertDrawnNote(
            DrawnNoteEntity(
                bookId = bookId,
                location = LocationCodec.encode(location),
                positionLabel = positionLabel,
                progress = progress,
                aspect = InkStyle.DRAWN_NOTE_ASPECT,
                createdAt = now(),
                updatedAt = now(),
            ),
        )

    fun drawnNotes(bookId: Long): Flow<List<DrawnNoteEntity>> = dao.observeDrawnNotes(bookId)

    val allDrawnNotes: Flow<List<DrawnNoteWithBook>> = dao.observeAllDrawnNotes()

    fun observeDrawnNote(id: Long): Flow<DrawnNoteEntity?> = dao.observeDrawnNote(id)

    suspend fun deleteDrawnNote(id: Long) = dao.deleteDrawnNote(id)

    /** A drawn note the user left without any ink is not kept. */
    suspend fun discardIfEmpty(noteId: Long): Boolean {
        if (dao.noteStrokeCount(noteId) > 0) return false
        dao.deleteDrawnNote(noteId)
        return true
    }
}
