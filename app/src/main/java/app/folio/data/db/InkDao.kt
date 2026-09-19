package app.folio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InkDao {

    // Strokes
    @Query("SELECT * FROM ink_strokes WHERE bookId = :bookId AND page IS NOT NULL ORDER BY createdAt, id")
    fun observePageStrokes(bookId: Long): Flow<List<InkStrokeEntity>>

    @Query("SELECT * FROM ink_strokes WHERE drawnNoteId = :noteId ORDER BY createdAt, id")
    fun observeNoteStrokes(noteId: Long): Flow<List<InkStrokeEntity>>

    @Query("SELECT * FROM ink_strokes WHERE drawnNoteId IS NOT NULL ORDER BY createdAt, id")
    fun observeAllNoteStrokes(): Flow<List<InkStrokeEntity>>

    @Query("SELECT COUNT(*) FROM ink_strokes WHERE drawnNoteId = :noteId")
    suspend fun noteStrokeCount(noteId: Long): Int

    @Query("SELECT * FROM ink_strokes WHERE id IN (:ids)")
    suspend fun strokes(ids: List<Long>): List<InkStrokeEntity>

    @Insert
    suspend fun insertStroke(stroke: InkStrokeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrokes(strokes: List<InkStrokeEntity>)

    @Query("DELETE FROM ink_strokes WHERE id IN (:ids)")
    suspend fun deleteStrokes(ids: List<Long>)

    @Query("SELECT * FROM ink_strokes")
    suspend fun allStrokes(): List<InkStrokeEntity>

    @Query("DELETE FROM ink_strokes")
    suspend fun deleteAllStrokes()

    // Drawn notes
    @Query("SELECT * FROM drawn_notes WHERE bookId = :bookId ORDER BY progress, createdAt")
    fun observeDrawnNotes(bookId: Long): Flow<List<DrawnNoteEntity>>

    @Query(
        """SELECT d.*, b.displayTitle AS bookTitle, b.format AS bookFormat FROM drawn_notes d
           JOIN books b ON b.id = d.bookId WHERE b.removedAt IS NULL ORDER BY d.updatedAt DESC""",
    )
    fun observeAllDrawnNotes(): Flow<List<DrawnNoteWithBook>>

    @Query("SELECT * FROM drawn_notes WHERE id = :id")
    fun observeDrawnNote(id: Long): Flow<DrawnNoteEntity?>

    @Insert
    suspend fun insertDrawnNote(note: DrawnNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrawnNotes(notes: List<DrawnNoteEntity>)

    @Query("UPDATE drawn_notes SET updatedAt = :at WHERE id = :id")
    suspend fun touchDrawnNote(id: Long, at: Long)

    @Query("DELETE FROM drawn_notes WHERE id = :id")
    suspend fun deleteDrawnNote(id: Long)

    @Query("SELECT * FROM drawn_notes")
    suspend fun allDrawnNotes(): List<DrawnNoteEntity>

    @Query("DELETE FROM drawn_notes")
    suspend fun deleteAllDrawnNotes()
}
