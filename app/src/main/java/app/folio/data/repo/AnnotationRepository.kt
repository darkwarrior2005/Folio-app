package app.folio.data.repo

import app.folio.core.model.BookLocation
import app.folio.core.model.HighlightColor
import app.folio.data.db.AnnotationDao
import app.folio.data.db.BookmarkEntity
import app.folio.data.db.BookmarkWithBook
import app.folio.data.db.FolioDatabase
import app.folio.data.db.HighlightEntity
import app.folio.data.db.HighlightWithBook
import app.folio.data.db.LocationCodec
import app.folio.data.db.NoteEntity
import app.folio.data.db.NoteWithBook
import kotlinx.coroutines.flow.Flow

class AnnotationRepository(
    db: FolioDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val dao: AnnotationDao = db.annotations()

    // ---- Bookmarks -------------------------------------------------------------

    fun bookmarks(bookId: Long): Flow<List<BookmarkEntity>> = dao.observeBookmarks(bookId)

    val allBookmarks: Flow<List<BookmarkWithBook>> = dao.observeAllBookmarks()

    suspend fun addBookmark(
        bookId: Long,
        location: BookLocation,
        positionLabel: String,
        progress: Float,
        title: String? = null,
        note: String? = null,
    ): Long = dao.insertBookmark(
        BookmarkEntity(
            bookId = bookId,
            location = LocationCodec.encode(location),
            positionLabel = positionLabel,
            progress = progress,
            title = title,
            note = note,
            createdAt = now(),
            updatedAt = now(),
        ),
    )

    suspend fun updateBookmark(bookmark: BookmarkEntity) =
        dao.updateBookmark(bookmark.copy(updatedAt = now()))

    suspend fun deleteBookmark(id: Long) = dao.deleteBookmark(id)

    suspend fun bookmarksOnce(bookId: Long): List<BookmarkEntity> = dao.bookmarks(bookId)

    /** Used by the reader to toggle the bookmark button for the current page. */
    suspend fun bookmarkAt(bookId: Long, page: Int?, progress: Float): BookmarkEntity? =
        dao.bookmarks(bookId).firstOrNull { bookmark ->
            val location = LocationCodec.decode(bookmark.location)
            if (page != null && location?.page != null) {
                location.page == page
            } else {
                kotlin.math.abs(bookmark.progress - progress) < PROGRESS_EPSILON
            }
        }

    // ---- Highlights ------------------------------------------------------------

    fun highlights(bookId: Long): Flow<List<HighlightEntity>> = dao.observeHighlights(bookId)

    val allHighlights: Flow<List<HighlightWithBook>> = dao.observeAllHighlights()

    suspend fun addHighlight(
        bookId: Long,
        location: BookLocation,
        positionLabel: String,
        progress: Float,
        text: String,
        color: HighlightColor,
        note: String? = null,
        page: Int? = null,
        rangeStart: Int? = null,
        rangeEnd: Int? = null,
    ): Long = dao.insertHighlight(
        HighlightEntity(
            bookId = bookId,
            location = LocationCodec.encode(location),
            positionLabel = positionLabel,
            progress = progress,
            text = text,
            color = color,
            note = note,
            page = page,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            createdAt = now(),
            updatedAt = now(),
        ),
    )

    suspend fun updateHighlight(highlight: HighlightEntity) =
        dao.updateHighlight(highlight.copy(updatedAt = now()))

    suspend fun setHighlightColor(id: Long, color: HighlightColor) {
        val highlight = dao.highlight(id) ?: return
        dao.updateHighlight(highlight.copy(color = color, updatedAt = now()))
    }

    suspend fun setHighlightNote(id: Long, note: String?) {
        val highlight = dao.highlight(id) ?: return
        dao.updateHighlight(highlight.copy(note = note?.takeIf { it.isNotBlank() }, updatedAt = now()))
    }

    suspend fun deleteHighlight(id: Long) = dao.deleteHighlight(id)

    suspend fun highlightsOnce(bookId: Long): List<HighlightEntity> = dao.highlights(bookId)

    // ---- Notes -----------------------------------------------------------------

    fun notes(bookId: Long): Flow<List<NoteEntity>> = dao.observeNotes(bookId)

    val allNotes: Flow<List<NoteWithBook>> = dao.observeAllNotes()

    suspend fun addNote(
        bookId: Long,
        location: BookLocation,
        positionLabel: String,
        progress: Float,
        text: String,
    ): Long = dao.insertNote(
        NoteEntity(
            bookId = bookId,
            location = LocationCodec.encode(location),
            positionLabel = positionLabel,
            progress = progress,
            text = text,
            createdAt = now(),
            updatedAt = now(),
        ),
    )

    suspend fun updateNote(note: NoteEntity) = dao.updateNote(note.copy(updatedAt = now()))

    suspend fun deleteNote(id: Long) = dao.deleteNote(id)

    suspend fun notesOnce(bookId: Long): List<NoteEntity> = dao.notes(bookId)

    suspend fun allHighlightsOnce(): List<HighlightEntity> = dao.allHighlights()

    suspend fun allNotesOnce(): List<NoteEntity> = dao.allNotes()

    suspend fun allBookmarksOnce(): List<BookmarkEntity> = dao.allBookmarks()

    companion object {
        private const val PROGRESS_EPSILON = 0.0005f
    }
}
