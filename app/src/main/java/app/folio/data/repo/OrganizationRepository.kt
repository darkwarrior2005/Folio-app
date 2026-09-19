package app.folio.data.repo

import androidx.room.withTransaction
import app.folio.core.model.TagNormalizer
import app.folio.data.db.BookTagEntity
import app.folio.data.db.CategoryEntity
import app.folio.data.db.CategoryWithCount
import app.folio.data.db.CollectionBookEntity
import app.folio.data.db.CollectionEntity
import app.folio.data.db.CollectionWithCount
import app.folio.data.db.FolioDatabase
import app.folio.data.db.QueueItemEntity
import app.folio.data.db.TagEntity
import app.folio.data.db.TagWithCount
import kotlinx.coroutines.flow.Flow

/** Categories, collections, tags and the reading queue: the virtual organization of the library. */
class OrganizationRepository(
    private val db: FolioDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val tagDao = db.tags()
    private val categoryDao = db.categories()
    private val collectionDao = db.collections()
    private val readingDao = db.reading()

    val tags: Flow<List<TagWithCount>> = tagDao.observeWithCounts()
    val allTags: Flow<List<TagEntity>> = tagDao.observeAll()
    val categories: Flow<List<CategoryWithCount>> = categoryDao.observeWithCounts()
    val collections: Flow<List<CollectionWithCount>> = collectionDao.observeWithCounts()
    val collectionLinks: Flow<List<CollectionBookEntity>> = collectionDao.observeLinks()
    val queue: Flow<List<QueueItemEntity>> = readingDao.observeQueue()

    fun observeCollection(id: Long): Flow<CollectionEntity?> = collectionDao.observe(id)

    fun tagsForBook(bookId: Long): Flow<List<TagEntity>> = tagDao.observeForBook(bookId)

    // ---- Tags ------------------------------------------------------------------

    /** Returns the existing tag when one already matches, ignoring case and spacing. */
    suspend fun ensureTag(name: String): Long? {
        val clean = TagNormalizer.clean(name)
        if (clean.isBlank()) return null
        val key = TagNormalizer.key(clean)
        tagDao.findByKey(key)?.let { return it.id }
        val id = tagDao.insert(TagEntity(name = clean, nameKey = key, createdAt = now()))
        return if (id > 0) id else tagDao.findByKey(key)?.id
    }

    suspend fun ensureTags(names: List<String>): List<Long> =
        names.mapNotNull { ensureTag(it) }.distinct()

    suspend fun currentTagIds(bookId: Long): List<Long> = tagDao.forBook(bookId).map { it.id }

    suspend fun addTagsToBooks(bookIds: List<Long>, names: List<String>) {
        val tagIds = ensureTags(names)
        if (tagIds.isEmpty() || bookIds.isEmpty()) return
        tagDao.link(bookIds.flatMap { bookId -> tagIds.map { BookTagEntity(bookId, it) } })
    }

    suspend fun removeTagFromBooks(tagId: Long, bookIds: List<Long>) = tagDao.unlink(tagId, bookIds)

    suspend fun renameTag(tagId: Long, newName: String) {
        val clean = TagNormalizer.clean(newName)
        if (clean.isBlank()) return
        val key = TagNormalizer.key(clean)
        db.withTransaction {
            val existing = tagDao.findByKey(key)
            if (existing != null && existing.id != tagId) {
                // Renaming onto an existing tag merges the two.
                tagDao.moveLinks(from = tagId, into = existing.id)
                tagDao.delete(tagId)
            } else {
                tagDao.rename(tagId, clean, key)
            }
        }
    }

    suspend fun deleteTag(tagId: Long) = tagDao.delete(tagId)

    suspend fun mergeTags(fromId: Long, intoId: Long) {
        if (fromId == intoId) return
        db.withTransaction {
            tagDao.moveLinks(fromId, intoId)
            tagDao.delete(fromId)
        }
    }

    suspend fun tagByKey(key: String): TagEntity? = tagDao.findByKey(key)

    suspend fun allTagsOnce(): List<TagEntity> = tagDao.all()

    // ---- Categories ------------------------------------------------------------

    suspend fun ensureCategory(name: String): Long? {
        val clean = TagNormalizer.clean(name)
        if (clean.isBlank()) return null
        val key = TagNormalizer.key(clean)
        categoryDao.findByKey(key)?.let { return it.id }
        val id = categoryDao.insert(
            CategoryEntity(name = clean, nameKey = key, sortOrder = categoryDao.maxOrder() + 1, createdAt = now()),
        )
        return if (id > 0) id else categoryDao.findByKey(key)?.id
    }

    suspend fun renameCategory(id: Long, newName: String) {
        val clean = TagNormalizer.clean(newName)
        if (clean.isBlank()) return
        categoryDao.rename(id, clean, TagNormalizer.key(clean))
    }

    /** Books in the category keep existing; they simply lose the category. */
    suspend fun deleteCategory(id: Long) = categoryDao.delete(id)

    suspend fun reorderCategories(orderedIds: List<Long>) {
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> categoryDao.setOrder(id, index) }
        }
    }

    suspend fun allCategoriesOnce(): List<CategoryEntity> = categoryDao.all()

    // ---- Collections -----------------------------------------------------------

    suspend fun createCollection(name: String, icon: String? = null, colorArgb: Long? = null): Long {
        val clean = TagNormalizer.clean(name)
        return collectionDao.insert(
            CollectionEntity(
                name = clean,
                icon = icon,
                colorArgb = colorArgb,
                sortOrder = collectionDao.maxOrder() + 1,
                createdAt = now(),
            ),
        )
    }

    suspend fun updateCollection(collection: CollectionEntity) = collectionDao.update(collection)

    suspend fun deleteCollection(id: Long) = collectionDao.delete(id)

    suspend fun addToCollection(collectionId: Long, bookIds: List<Long>) {
        if (bookIds.isEmpty()) return
        db.withTransaction {
            var position = collectionDao.maxPosition(collectionId) + 1
            val links = bookIds.map { bookId ->
                CollectionBookEntity(collectionId, bookId, position++, now())
            }
            collectionDao.addLinks(links)
        }
    }

    suspend fun removeFromCollection(collectionId: Long, bookIds: List<Long>) =
        collectionDao.removeLinks(collectionId, bookIds)

    suspend fun reorderCollection(collectionId: Long, orderedBookIds: List<Long>) {
        db.withTransaction {
            orderedBookIds.forEachIndexed { index, bookId ->
                collectionDao.setPosition(collectionId, bookId, index)
            }
        }
    }

    suspend fun collectionLinksOnce(): List<CollectionBookEntity> = collectionDao.allLinks()

    suspend fun allCollectionsOnce(): List<CollectionEntity> = collectionDao.all()

    // ---- Reading queue ---------------------------------------------------------

    suspend fun enqueue(bookId: Long) {
        db.withTransaction {
            readingDao.enqueue(QueueItemEntity(bookId, readingDao.maxQueuePosition() + 1, now()))
        }
    }

    suspend fun dequeue(bookId: Long) = readingDao.dequeue(bookId)

    suspend fun reorderQueue(orderedBookIds: List<Long>) {
        db.withTransaction {
            orderedBookIds.forEachIndexed { index, bookId -> readingDao.setQueuePosition(bookId, index) }
        }
    }

    suspend fun queueOnce(): List<QueueItemEntity> = readingDao.queue()
}
