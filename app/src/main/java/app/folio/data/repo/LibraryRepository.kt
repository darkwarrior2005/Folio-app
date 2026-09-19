package app.folio.data.repo

import android.graphics.Bitmap
import androidx.room.withTransaction
import app.folio.core.model.BookFormat
import app.folio.core.model.BookLocation
import app.folio.core.model.BookMetadata
import app.folio.core.model.IndexState
import app.folio.core.model.LibraryBook
import app.folio.core.model.LibraryQuery
import app.folio.core.model.MetadataResolver
import app.folio.core.model.ReadingStatus
import app.folio.core.model.StorageMode
import app.folio.core.model.TagRef
import app.folio.data.db.BookEntity
import app.folio.data.db.CategoryEntity
import app.folio.data.db.FolioDatabase
import app.folio.data.db.LocationCodec
import app.folio.data.db.ReadingProgressEntity
import app.folio.data.db.TagEntity
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** Everything the user edits about a book in one shot. Null means "no override, use imported". */
data class BookEdit(
    val title: String?,
    val author: String?,
    val description: String?,
    val series: String?,
    val volume: Double?,
    val year: Int?,
    val language: String?,
    val publisher: String?,
    val categoryId: Long?,
    val status: ReadingStatus,
    val tagNames: List<String>,
)

data class NewBook(
    val uri: String,
    val fileName: String,
    val fileSize: Long,
    val fileHash: String,
    val mimeType: String?,
    val format: BookFormat,
    val storage: StorageMode,
    val imported: BookMetadata,
    val pageCount: Int?,
    val passwordProtected: Boolean = false,
    val indexState: IndexState = IndexState.PENDING,
)

class LibraryRepository(
    private val db: FolioDatabase,
    private val files: FileAccess,
    private val cache: CacheStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val bookDao = db.books()
    private val tagDao = db.tags()
    private val categoryDao = db.categories()
    private val collectionDao = db.collections()
    private val readingDao = db.reading()
    private val searchDao = db.search()

    private data class Organization(
        val categories: Map<Long, CategoryEntity>,
        val tags: Map<Long, TagEntity>,
        val tagsByBook: Map<Long, List<TagRef>>,
        val collectionsByBook: Map<Long, Set<Long>>,
        val queued: Set<Long>,
    )

    private val organization: Flow<Organization> = combine(
        categoryDao.observeWithCounts(),
        tagDao.observeAll(),
        tagDao.observeLinks(),
        collectionDao.observeLinks(),
        readingDao.observeQueue(),
    ) { categories, tags, tagLinks, collectionLinks, queue ->
        val tagsById = tags.associateBy { it.id }
        Organization(
            categories = categories.associate { it.category.id to it.category },
            tags = tagsById,
            tagsByBook = tagLinks.groupBy { it.bookId }
                .mapValues { (_, links) ->
                    links.mapNotNull { link -> tagsById[link.tagId]?.let { TagRef(it.id, it.name) } }
                        .sortedBy { it.name.lowercase() }
                },
            collectionsByBook = collectionLinks.groupBy { it.bookId }
                .mapValues { (_, links) -> links.map { it.collectionId }.toSet() },
            queued = queue.map { it.bookId }.toSet(),
        )
    }

    /** Every book in the library, joined with its organization. Drives the whole library UI. */
    val libraryBooks: Flow<List<LibraryBook>> =
        combine(bookDao.observeLibrary(), organization) { books, org ->
            books.map { it.toLibraryBook(org) }
        }

    val removedBooks: Flow<List<LibraryBook>> =
        combine(bookDao.observeRemoved(), organization) { books, org ->
            books.map { it.toLibraryBook(org) }
        }

    fun observeBook(id: Long): Flow<LibraryBook?> =
        combine(bookDao.observe(id), organization) { book, org -> book?.toLibraryBook(org) }

    fun observeEntity(id: Long): Flow<BookEntity?> = bookDao.observe(id)

    suspend fun entity(id: Long): BookEntity? = bookDao.get(id)

    val progress: Flow<Map<Long, ReadingProgressEntity>> =
        readingDao.observeAllProgress().map { list -> list.associateBy { it.bookId } }

    private fun BookEntity.toLibraryBook(org: Organization): LibraryBook {
        val meta = MetadataResolver.resolve(imported, overrides)
        return LibraryBook(
            id = id,
            uri = uri,
            title = displayTitle,
            author = displayAuthor,
            series = displaySeries,
            volume = meta.volume,
            year = meta.year,
            fileName = fileName,
            format = format,
            status = status,
            favorite = favorite,
            progress = progress,
            pageCount = pageCount,
            fileSize = fileSize,
            dateAdded = dateAdded,
            lastOpenedAt = lastOpenedAt,
            finishedAt = finishedAt,
            categoryId = categoryId,
            categoryName = categoryId?.let { org.categories[it]?.name },
            tags = org.tagsByBook[id].orEmpty(),
            collectionIds = org.collectionsByBook[id].orEmpty(),
            coverPath = if (coverHidden) null else (customCoverPath ?: importedCoverPath),
            missing = missing,
            customOrder = customOrder,
            sortTitle = sortTitle,
            queued = id in org.queued,
        )
    }

    // ---- Import ----------------------------------------------------------------

    suspend fun findDuplicate(hash: String, size: Long): BookEntity? = bookDao.findDuplicate(hash, size)

    suspend fun insert(book: NewBook): Long {
        val title = MetadataResolver.displayTitle(book.imported, BookMetadata(), book.fileName)
        val entity = BookEntity(
            uri = book.uri,
            fileName = book.fileName,
            fileSize = book.fileSize,
            fileHash = book.fileHash,
            mimeType = book.mimeType,
            format = book.format,
            storage = book.storage,
            pageCount = book.pageCount,
            imported = book.imported,
            displayTitle = title,
            displayAuthor = book.imported.author,
            displaySeries = book.imported.series,
            sortTitle = MetadataResolver.sortKey(title),
            dateAdded = now(),
            passwordProtected = book.passwordProtected,
            indexState = book.indexState,
        )
        return bookDao.insert(entity)
    }

    suspend fun setImportedCover(bookId: Long, bitmap: Bitmap) {
        val path = cache.writeCover(bookId, bitmap, custom = false) ?: return
        val book = bookDao.get(bookId) ?: return
        bookDao.update(book.copy(importedCoverPath = path))
    }

    /** Re-reading the file may improve imported metadata; user overrides are never touched. */
    suspend fun applyRescan(bookId: Long, imported: BookMetadata, pageCount: Int?) {
        val book = bookDao.get(bookId) ?: return
        val updated = book.copy(imported = imported, pageCount = pageCount ?: book.pageCount)
        bookDao.update(updated.withResolvedDisplay())
    }

    /** Replace the file behind an existing book (the "Replace" answer to a duplicate). */
    suspend fun replaceFile(bookId: Long, book: NewBook) {
        val existing = bookDao.get(bookId) ?: return
        val updated = existing.copy(
            uri = book.uri,
            fileName = book.fileName,
            fileSize = book.fileSize,
            fileHash = book.fileHash,
            mimeType = book.mimeType,
            format = book.format,
            storage = book.storage,
            imported = book.imported,
            pageCount = book.pageCount ?: existing.pageCount,
            missing = false,
            indexState = IndexState.PENDING,
        )
        bookDao.update(updated.withResolvedDisplay())
        searchDao.deleteForBook(bookId)
    }

    private fun BookEntity.withResolvedDisplay(): BookEntity {
        val meta = MetadataResolver.resolve(imported, overrides)
        val title = MetadataResolver.displayTitle(imported, overrides, fileName)
        return copy(
            displayTitle = title,
            displayAuthor = meta.author,
            displaySeries = meta.series,
            sortTitle = MetadataResolver.sortKey(title),
        )
    }

    // ---- Editing ---------------------------------------------------------------

    suspend fun applyEdit(bookId: Long, edit: BookEdit, tagIds: List<Long>) {
        db.withTransaction {
            val book = bookDao.get(bookId) ?: return@withTransaction
            val imported = book.imported
            val overrides = BookMetadata(
                title = MetadataResolver.overrideFor(edit.title, imported.title),
                author = MetadataResolver.overrideFor(edit.author, imported.author),
                description = MetadataResolver.overrideFor(edit.description, imported.description),
                series = MetadataResolver.overrideFor(edit.series, imported.series),
                volume = MetadataResolver.overrideFor(edit.volume, imported.volume),
                year = MetadataResolver.overrideFor(edit.year, imported.year),
                language = MetadataResolver.overrideFor(edit.language, imported.language),
                publisher = MetadataResolver.overrideFor(edit.publisher, imported.publisher),
            )
            val statusChanged = edit.status != book.status
            val updated = book.copy(
                overrides = overrides,
                categoryId = edit.categoryId,
                status = edit.status,
                statusManual = if (statusChanged) true else book.statusManual,
                finishedAt = when {
                    edit.status == ReadingStatus.FINISHED -> book.finishedAt ?: now()
                    else -> null
                },
            ).withResolvedDisplay()
            bookDao.update(updated)
            tagDao.clearForBook(bookId)
            if (tagIds.isNotEmpty()) {
                tagDao.link(tagIds.map { app.folio.data.db.BookTagEntity(bookId, it) })
            }
        }
    }

    suspend fun setFavorite(ids: List<Long>, favorite: Boolean) = bookDao.setFavorite(ids, favorite)

    suspend fun setCategory(ids: List<Long>, categoryId: Long?) = bookDao.setCategory(ids, categoryId)

    suspend fun setStatus(ids: List<Long>, status: ReadingStatus) {
        db.withTransaction {
            bookDao.setStatus(ids, status.name, manual = true, now = now())
            when (status) {
                ReadingStatus.FINISHED -> bookDao.setProgressValue(ids, 1f)
                ReadingStatus.UNREAD -> {
                    bookDao.setProgressValue(ids, 0f)
                    readingDao.deleteProgress(ids)
                }
                else -> Unit
            }
        }
    }

    suspend fun setReaderPrefs(bookId: Long, prefsJson: String?) {
        val book = bookDao.get(bookId) ?: return
        bookDao.update(book.copy(readerPrefs = prefsJson))
    }

    // ---- Covers ----------------------------------------------------------------

    suspend fun setCustomCover(bookId: Long, bitmap: Bitmap) {
        val path = cache.writeCover(bookId, bitmap, custom = true) ?: return
        val book = bookDao.get(bookId) ?: return
        bookDao.update(book.copy(customCoverPath = path, coverHidden = false))
    }

    suspend fun hideCover(bookId: Long) {
        val book = bookDao.get(bookId) ?: return
        bookDao.update(book.copy(coverHidden = true))
    }

    suspend fun restoreOriginalCover(bookId: Long) {
        val book = bookDao.get(bookId) ?: return
        cache.coverFile(bookId, custom = true).delete()
        bookDao.update(book.copy(customCoverPath = null, coverHidden = false))
    }

    // ---- Reading position ------------------------------------------------------

    suspend fun position(bookId: Long): BookLocation? =
        LocationCodec.decode(readingDao.progress(bookId)?.location)

    suspend fun progressRow(bookId: Long): ReadingProgressEntity? = readingDao.progress(bookId)

    /**
     * Saves where the reader is. Written in one transaction so a force close can never leave the
     * book row and the saved position disagreeing.
     */
    suspend fun saveProgress(
        bookId: Long,
        location: BookLocation,
        progress: Float,
        page: Int?,
        pageCount: Int?,
    ) {
        db.withTransaction {
            val book = bookDao.get(bookId) ?: return@withTransaction
            val clamped = progress.coerceIn(0f, 1f)
            readingDao.upsertProgress(
                ReadingProgressEntity(
                    bookId = bookId,
                    location = LocationCodec.encode(location),
                    progress = clamped,
                    page = page,
                    pageCount = pageCount ?: book.pageCount,
                    updatedAt = now(),
                ),
            )
            bookDao.updateProgress(bookId, clamped, pageCount, now())
            val auto = autoStatus(book, clamped)
            if (auto != null && auto != book.status) {
                bookDao.setStatus(listOf(bookId), auto.name, manual = false, now = now())
            }
        }
    }

    private fun autoStatus(book: BookEntity, progress: Float): ReadingStatus? = when {
        book.statusManual && book.status != ReadingStatus.UNREAD -> null
        progress >= LibraryQuery.FINISHED_PROGRESS -> ReadingStatus.FINISHED
        progress > 0f && book.status == ReadingStatus.UNREAD -> ReadingStatus.READING
        else -> null
    }

    suspend fun recordOpen(bookId: Long) {
        db.withTransaction {
            val book = bookDao.get(bookId) ?: return@withTransaction
            bookDao.markOpened(bookId, now())
            if (book.status == ReadingStatus.UNREAD) {
                bookDao.setStatus(listOf(bookId), ReadingStatus.READING.name, manual = false, now = now())
            }
        }
    }

    // ---- Availability, removal, files ------------------------------------------

    /** Detects files moved or deleted outside the app. Metadata is kept either way. */
    suspend fun refreshAvailability(): Int {
        var missingCount = 0
        bookDao.all().forEach { book ->
            if (book.removedAt != null) return@forEach
            val exists = files.exists(book.uri)
            if (!exists) missingCount++
            if (exists == book.missing) bookDao.setMissing(book.id, !exists)
        }
        return missingCount
    }

    suspend fun isAvailable(bookId: Long): Boolean {
        val book = bookDao.get(bookId) ?: return false
        val exists = files.exists(book.uri)
        if (exists == book.missing) bookDao.setMissing(bookId, !exists)
        return exists
    }

    /** Point an existing book at a file the user located again. */
    suspend fun relocate(bookId: Long, uri: String, fileName: String, fileSize: Long, hash: String) {
        val book = bookDao.get(bookId) ?: return
        bookDao.update(
            book.copy(uri = uri, fileName = fileName, fileSize = fileSize, fileHash = hash, missing = false),
        )
    }

    /** Remove from library: metadata goes to "Recently removed", the file is left alone. */
    suspend fun removeFromLibrary(ids: List<Long>) = bookDao.softRemove(ids, now())

    suspend fun restore(ids: List<Long>) = bookDao.restore(ids)

    /** Forget a book completely: rows, covers and index. The original file still is not touched. */
    suspend fun purge(ids: List<Long>) {
        db.withTransaction {
            ids.forEach { id ->
                val book = bookDao.get(id)
                searchDao.deleteForBook(id)
                cache.deleteCovers(id)
                if (book?.storage == StorageMode.COPIED) {
                    files.deleteFile(book.uri)
                }
            }
            bookDao.delete(ids)
        }
    }

    suspend fun purgeExpiredTrash(retentionDays: Int) {
        if (retentionDays <= 0) return
        val cutoff = now() - retentionDays * 24L * 60 * 60 * 1000
        val expired = bookDao.removedBefore(cutoff).map { it.id }
        if (expired.isNotEmpty()) purge(expired)
    }

    /** Explicit, confirmed destructive action. */
    suspend fun deleteFileAndForget(bookId: Long): Boolean {
        val book = bookDao.get(bookId) ?: return false
        val deleted = files.deleteFile(book.uri)
        if (deleted) purge(listOf(bookId))
        return deleted
    }

    suspend fun renamePhysicalFile(bookId: Long, newName: String): Boolean {
        val book = bookDao.get(bookId) ?: return false
        val newUri = files.renameFile(book.uri, newName) ?: return false
        bookDao.update(book.copy(uri = newUri, fileName = newName))
        return true
    }

    // ---- Indexing --------------------------------------------------------------

    suspend fun pendingIndex(limit: Int): List<BookEntity> = bookDao.pendingIndex(limit)

    suspend fun setIndexState(bookId: Long, state: IndexState) = bookDao.setIndexState(bookId, state.name)

    suspend fun bookCount(): Int = bookDao.count()

    suspend fun librarySize(): Long = bookDao.totalSize()

    suspend fun copiedSize(): Long = bookDao.copiedSize()

    suspend fun allEntities(): List<BookEntity> = bookDao.all()
}
