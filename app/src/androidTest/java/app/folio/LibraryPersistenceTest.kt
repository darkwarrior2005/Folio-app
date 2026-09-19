package app.folio

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.folio.core.model.BookFormat
import app.folio.core.model.BookLocation
import app.folio.core.model.BookMetadata
import app.folio.core.model.ReadingStatus
import app.folio.core.model.StorageMode
import app.folio.data.db.FolioDatabase
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.repo.BookEdit
import app.folio.data.repo.LibraryRepository
import app.folio.data.repo.NewBook
import app.folio.data.repo.OrganizationRepository
import app.folio.data.repo.AnnotationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The offline promises that matter most: a reading position, custom metadata, tags and
 * annotations all survive in the database exactly as the user left them.
 */
@RunWith(AndroidJUnit4::class)
class LibraryPersistenceTest {

    private lateinit var db: FolioDatabase
    private lateinit var library: LibraryRepository
    private lateinit var organization: OrganizationRepository
    private lateinit var annotations: AnnotationRepository

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FolioDatabase::class.java).build()
        library = LibraryRepository(db, FileAccess(context), CacheStore(context))
        organization = OrganizationRepository(db)
        annotations = AnnotationRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertSample(): Long = library.insert(
        NewBook(
            uri = "content://test/three-body.pdf",
            fileName = "The.Three.Body.Problem.English.pdf",
            fileSize = 12_345,
            fileHash = "hash-1",
            mimeType = "application/pdf",
            format = BookFormat.PDF,
            storage = StorageMode.LINKED,
            imported = BookMetadata(title = "The Three Body Problem", author = "Liu Cixin", year = 2008),
            pageCount = 302,
        ),
    )

    @Test
    fun importedBookKeepsFileNameAndShowsImportedTitle() = runTest {
        val id = insertSample()
        val book = library.observeBook(id).first()
        assertNotNull(book)
        assertEquals("The Three Body Problem", book!!.title)
        assertEquals("The.Three.Body.Problem.English.pdf", book.fileName)
    }

    @Test
    fun userEditsSurviveARescan() = runTest {
        val id = insertSample()
        val tagIds = organization.ensureTags(listOf("Sci-Fi", "Physics", "sci-fi"))
        assertEquals(2, tagIds.size) // "sci-fi" is the same tag as "Sci-Fi"

        library.applyEdit(
            bookId = id,
            edit = BookEdit(
                title = "The Three-Body Problem",
                author = "Cixin Liu",
                description = null,
                series = "Remembrance of Earth's Past",
                volume = 1.0,
                year = 2008,
                language = null,
                publisher = null,
                categoryId = organization.ensureCategory("Science Fiction"),
                status = ReadingStatus.READING,
                tagNames = listOf("Sci-Fi", "Physics"),
            ),
            tagIds = tagIds,
        )

        // Re-reading the file reports the original metadata again.
        library.applyRescan(
            id,
            BookMetadata(title = "The Three Body Problem", author = "Liu Cixin", year = 2008),
            pageCount = 302,
        )

        val book = library.observeBook(id).first()!!
        assertEquals("The Three-Body Problem", book.title)
        assertEquals("Cixin Liu", book.author)
        assertEquals(2, book.tags.size)
        assertEquals("Science Fiction", book.categoryName)
    }

    @Test
    fun readingPositionRoundTrips() = runTest {
        val id = insertSample()
        val location = BookLocation(page = 49, offset = 0.25, totalProgression = 0.16, label = "50")
        library.saveProgress(id, location, progress = 0.16f, page = 49, pageCount = 302)

        val restored = library.position(id)
        assertNotNull(restored)
        assertEquals(49, restored!!.page)
        assertEquals(0.25, restored.offset, 0.0001)

        val book = library.observeBook(id).first()!!
        assertEquals(0.16f, book.progress, 0.001f)
        // Opening a book and reading moves it out of "unread" on its own.
        assertEquals(ReadingStatus.READING, book.status)
    }

    @Test
    fun bookmarksAndHighlightsPersistPerBook() = runTest {
        val id = insertSample()
        val location = BookLocation(page = 49, totalProgression = 0.16)
        annotations.addBookmark(id, location, "50", 0.16f, title = "Key passage")
        annotations.addHighlight(
            bookId = id,
            location = location,
            positionLabel = "50",
            progress = 0.16f,
            text = "The universe is a dark forest.",
            color = app.folio.core.model.HighlightColor.BLUE,
            note = "Central metaphor",
            page = 49,
            rangeStart = 120,
            rangeEnd = 150,
        )

        assertEquals(1, annotations.bookmarksOnce(id).size)
        val highlight = annotations.highlightsOnce(id).single()
        assertEquals(120, highlight.rangeStart)
        assertEquals("Central metaphor", highlight.note)
    }

    @Test
    fun removingFromLibraryKeepsMetadataUntilPurged() = runTest {
        val id = insertSample()
        library.removeFromLibrary(listOf(id))
        assertTrue(library.libraryBooks.first().none { it.id == id })
        assertTrue(library.removedBooks.first().any { it.id == id })

        library.restore(listOf(id))
        assertTrue(library.libraryBooks.first().any { it.id == id })

        library.purge(listOf(id))
        assertNull(library.entity(id))
    }

    @Test
    fun duplicateDetectionMatchesOnHashAndSize() = runTest {
        insertSample()
        assertNotNull(library.findDuplicate("hash-1", 12_345))
        assertNull(library.findDuplicate("hash-1", 999))
    }

    @Test
    fun collectionsKeepTheirOrder() = runTest {
        val first = insertSample()
        val second = library.insert(
            NewBook(
                uri = "content://test/second.epub",
                fileName = "second.epub",
                fileSize = 5_000,
                fileHash = "hash-2",
                mimeType = "application/epub+zip",
                format = BookFormat.EPUB,
                storage = StorageMode.LINKED,
                imported = BookMetadata(title = "Second"),
                pageCount = null,
            ),
        )
        val collectionId = organization.createCollection("Currently Reading")
        organization.addToCollection(collectionId, listOf(first, second))
        organization.reorderCollection(collectionId, listOf(second, first))

        val links = organization.collectionLinksOnce()
            .filter { it.collectionId == collectionId }
            .sortedBy { it.position }
        assertEquals(listOf(second, first), links.map { it.bookId })
    }
}
