package app.folio.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class BookmarkWithBook(@Embedded val bookmark: BookmarkEntity, val bookTitle: String, val bookFormat: String)
data class HighlightWithBook(@Embedded val highlight: HighlightEntity, val bookTitle: String, val bookFormat: String)
data class NoteWithBook(@Embedded val note: NoteEntity, val bookTitle: String, val bookFormat: String)

@Dao
interface AnnotationDao {

    // Bookmarks
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY progress, createdAt")
    fun observeBookmarks(bookId: Long): Flow<List<BookmarkEntity>>

    @Query(
        """SELECT bm.*, b.displayTitle AS bookTitle, b.format AS bookFormat FROM bookmarks bm
           JOIN books b ON b.id = bm.bookId WHERE b.removedAt IS NULL ORDER BY bm.createdAt DESC""",
    )
    fun observeAllBookmarks(): Flow<List<BookmarkWithBook>>

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY progress")
    suspend fun bookmarks(bookId: Long): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Insert
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Insert
    suspend fun insertBookmarks(bookmarks: List<BookmarkEntity>)

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Long)

    // Highlights
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY progress, createdAt")
    fun observeHighlights(bookId: Long): Flow<List<HighlightEntity>>

    @Query(
        """SELECT h.*, b.displayTitle AS bookTitle, b.format AS bookFormat FROM highlights h
           JOIN books b ON b.id = h.bookId WHERE b.removedAt IS NULL ORDER BY h.createdAt DESC""",
    )
    fun observeAllHighlights(): Flow<List<HighlightWithBook>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY progress")
    suspend fun highlights(bookId: Long): List<HighlightEntity>

    @Query("SELECT * FROM highlights")
    suspend fun allHighlights(): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun highlight(id: Long): HighlightEntity?

    @Insert
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Insert
    suspend fun insertHighlights(highlights: List<HighlightEntity>)

    @Update
    suspend fun updateHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlight(id: Long)

    // Notes
    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY progress, createdAt")
    fun observeNotes(bookId: Long): Flow<List<NoteEntity>>

    @Query(
        """SELECT n.*, b.displayTitle AS bookTitle, b.format AS bookFormat FROM notes n
           JOIN books b ON b.id = n.bookId WHERE b.removedAt IS NULL ORDER BY n.updatedAt DESC""",
    )
    fun observeAllNotes(): Flow<List<NoteWithBook>>

    @Query("SELECT * FROM notes WHERE bookId = :bookId ORDER BY progress")
    suspend fun notes(bookId: Long): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun allNotes(): List<NoteEntity>

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Insert
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: Long)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAllBookmarks()

    @Query("DELETE FROM highlights")
    suspend fun deleteAllHighlights()

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}
