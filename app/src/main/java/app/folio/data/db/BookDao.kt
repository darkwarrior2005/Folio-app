package app.folio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE removedAt IS NULL")
    fun observeLibrary(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE removedAt IS NOT NULL ORDER BY removedAt DESC")
    fun observeRemoved(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<BookEntity>

    @Query("SELECT * FROM books")
    suspend fun all(): List<BookEntity>

    @Query("SELECT * FROM books WHERE fileHash = :hash AND fileSize = :size AND removedAt IS NULL LIMIT 1")
    suspend fun findDuplicate(hash: String, size: Long): BookEntity?

    @Query("SELECT * FROM books WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Insert
    suspend fun insertAll(books: List<BookEntity>): List<Long>

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    @Query("UPDATE books SET favorite = :favorite WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, favorite: Boolean)

    @Query(
        """UPDATE books SET status = :status, statusManual = :manual,
           finishedAt = CASE WHEN :status = 'FINISHED' THEN COALESCE(finishedAt, :now) ELSE NULL END
           WHERE id IN (:ids)""",
    )
    suspend fun setStatus(ids: List<Long>, status: String, manual: Boolean, now: Long)

    @Query("UPDATE books SET progress = :progress WHERE id IN (:ids)")
    suspend fun setProgressValue(ids: List<Long>, progress: Float)

    @Query("UPDATE books SET categoryId = :categoryId WHERE id IN (:ids)")
    suspend fun setCategory(ids: List<Long>, categoryId: Long?)

    @Query(
        """UPDATE books SET progress = :progress, lastOpenedAt = :now,
           pageCount = COALESCE(:pageCount, pageCount) WHERE id = :id""",
    )
    suspend fun updateProgress(id: Long, progress: Float, pageCount: Int?, now: Long)

    @Query("UPDATE books SET lastOpenedAt = :now WHERE id = :id")
    suspend fun markOpened(id: Long, now: Long)

    @Query("UPDATE books SET missing = :missing WHERE id = :id")
    suspend fun setMissing(id: Long, missing: Boolean)

    @Query("UPDATE books SET removedAt = :now WHERE id IN (:ids)")
    suspend fun softRemove(ids: List<Long>, now: Long)

    @Query("UPDATE books SET removedAt = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    @Query("SELECT * FROM books WHERE removedAt IS NOT NULL AND removedAt < :before")
    suspend fun removedBefore(before: Long): List<BookEntity>

    @Query(
        """SELECT * FROM books WHERE indexState IN ('PENDING', 'INDEXING')
           AND removedAt IS NULL AND missing = 0 ORDER BY dateAdded LIMIT :limit""",
    )
    suspend fun pendingIndex(limit: Int): List<BookEntity>

    @Query("UPDATE books SET indexState = :state WHERE id = :id")
    suspend fun setIndexState(id: Long, state: String)

    @Query("UPDATE books SET indexState = 'PENDING' WHERE indexState IN ('DONE', 'FAILED', 'INDEXING')")
    suspend fun resetIndexStates()

    @Query("UPDATE books SET customOrder = :order WHERE id = :id")
    suspend fun setCustomOrder(id: Long, order: Long)

    @Query("SELECT COUNT(*) FROM books WHERE removedAt IS NULL")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM books WHERE removedAt IS NULL")
    suspend fun totalSize(): Long

    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM books WHERE storage = 'COPIED'")
    suspend fun copiedSize(): Long
}
