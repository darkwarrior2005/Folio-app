package app.folio.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TagWithCount(@Embedded val tag: TagEntity, val bookCount: Int)
data class CategoryWithCount(@Embedded val category: CategoryEntity, val bookCount: Int)
data class CollectionWithCount(@Embedded val collection: CollectionEntity, val bookCount: Int)

@Dao
interface TagDao {
    @Query(
        """SELECT t.*, (SELECT COUNT(*) FROM book_tags bt JOIN books b ON b.id = bt.bookId
           WHERE bt.tagId = t.id AND b.removedAt IS NULL) AS bookCount
           FROM tags t ORDER BY t.nameKey""",
    )
    fun observeWithCounts(): Flow<List<TagWithCount>>

    @Query("SELECT * FROM tags ORDER BY nameKey")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY nameKey")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM book_tags")
    fun observeLinks(): Flow<List<BookTagEntity>>

    @Query("SELECT * FROM book_tags")
    suspend fun allLinks(): List<BookTagEntity>

    @Query("SELECT t.* FROM tags t JOIN book_tags bt ON bt.tagId = t.id WHERE bt.bookId = :bookId ORDER BY t.nameKey")
    fun observeForBook(bookId: Long): Flow<List<TagEntity>>

    @Query("SELECT t.* FROM tags t JOIN book_tags bt ON bt.tagId = t.id WHERE bt.bookId = :bookId ORDER BY t.nameKey")
    suspend fun forBook(bookId: Long): List<TagEntity>

    @Query("SELECT * FROM tags WHERE nameKey = :key LIMIT 1")
    suspend fun findByKey(key: String): TagEntity?

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun get(id: Long): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(links: List<BookTagEntity>)

    @Query("DELETE FROM book_tags WHERE tagId = :tagId AND bookId IN (:bookIds)")
    suspend fun unlink(tagId: Long, bookIds: List<Long>)

    @Query("DELETE FROM book_tags WHERE bookId = :bookId")
    suspend fun clearForBook(bookId: Long)

    @Query("UPDATE tags SET name = :name, nameKey = :key WHERE id = :id")
    suspend fun rename(id: Long, name: String, key: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE OR IGNORE book_tags SET tagId = :into WHERE tagId = :from")
    suspend fun moveLinks(from: Long, into: Long)

    @Query("DELETE FROM tags")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query(
        """SELECT c.*, (SELECT COUNT(*) FROM books b WHERE b.categoryId = c.id AND b.removedAt IS NULL) AS bookCount
           FROM categories c ORDER BY c.sortOrder, c.nameKey""",
    )
    fun observeWithCounts(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, nameKey")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE nameKey = :key LIMIT 1")
    suspend fun findByKey(key: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories")
    suspend fun maxOrder(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity): Long

    @Query("UPDATE categories SET name = :name, nameKey = :key WHERE id = :id")
    suspend fun rename(id: Long, name: String, key: String)

    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: Long, order: Int)

    /** Books keep existing: the foreign key sets their categoryId to NULL. */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface CollectionDao {
    @Query(
        """SELECT c.*, (SELECT COUNT(*) FROM collection_books cb JOIN books b ON b.id = cb.bookId
           WHERE cb.collectionId = c.id AND b.removedAt IS NULL) AS bookCount
           FROM collections c ORDER BY c.sortOrder, c.createdAt""",
    )
    fun observeWithCounts(): Flow<List<CollectionWithCount>>

    @Query("SELECT * FROM collections ORDER BY sortOrder, createdAt")
    suspend fun all(): List<CollectionEntity>

    @Query("SELECT * FROM collections WHERE id = :id")
    fun observe(id: Long): Flow<CollectionEntity?>

    @Query("SELECT * FROM collection_books ORDER BY collectionId, position")
    fun observeLinks(): Flow<List<CollectionBookEntity>>

    @Query("SELECT * FROM collection_books ORDER BY collectionId, position")
    suspend fun allLinks(): List<CollectionBookEntity>

    @Query("SELECT * FROM collection_books WHERE collectionId = :id ORDER BY position")
    suspend fun links(id: Long): List<CollectionBookEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM collection_books WHERE collectionId = :id")
    suspend fun maxPosition(id: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM collections")
    suspend fun maxOrder(): Int

    @Insert
    suspend fun insert(collection: CollectionEntity): Long

    @Update
    suspend fun update(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addLinks(links: List<CollectionBookEntity>)

    @Query("DELETE FROM collection_books WHERE collectionId = :collectionId AND bookId IN (:bookIds)")
    suspend fun removeLinks(collectionId: Long, bookIds: List<Long>)

    @Query("UPDATE collection_books SET position = :position WHERE collectionId = :collectionId AND bookId = :bookId")
    suspend fun setPosition(collectionId: Long, bookId: Long, position: Int)

    @Query("DELETE FROM collections")
    suspend fun deleteAll()
}
