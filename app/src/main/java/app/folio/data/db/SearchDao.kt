package app.folio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

data class FtsHit(
    val bookId: Long,
    val location: String,
    val label: String,
    val snippet: String,
)

@Dao
interface SearchDao {

    @Insert
    suspend fun insertChunks(chunks: List<BookTextFts>)

    @Query("DELETE FROM book_text WHERE bookId = :bookId")
    suspend fun deleteForBook(bookId: Long)

    @Query("DELETE FROM book_text")
    suspend fun deleteAll()

    /**
     * [match] must already be a sanitized FTS4 MATCH expression (see SearchQuery.toFtsMatch).
     * snippet() column index 3 is `text`.
     */
    @Query(
        """SELECT bookId, location, label, snippet(book_text, '[[', ']]', '…', 3, 14) AS snippet
           FROM book_text WHERE book_text MATCH :match LIMIT :limit""",
    )
    suspend fun search(match: String, limit: Int): List<FtsHit>

    @Query(
        """SELECT bookId, location, label, snippet(book_text, '[[', ']]', '…', 3, 14) AS snippet
           FROM book_text WHERE book_text MATCH :match AND bookId = :bookId LIMIT :limit""",
    )
    suspend fun searchInBook(match: String, bookId: Long, limit: Int): List<FtsHit>

    @Query("SELECT COUNT(DISTINCT bookId) FROM book_text")
    suspend fun indexedBookCount(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(text)), 0) FROM book_text")
    suspend fun indexedChars(): Long
}
