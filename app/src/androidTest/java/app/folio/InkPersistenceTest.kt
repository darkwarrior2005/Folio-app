package app.folio

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.folio.core.model.BookFormat
import app.folio.core.model.BookLocation
import app.folio.core.model.BookMetadata
import app.folio.core.model.InkPacking
import app.folio.core.model.InkPoint
import app.folio.core.model.InkTool
import app.folio.core.model.StorageMode
import app.folio.data.db.FolioDatabase
import app.folio.data.db.toInkStroke
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.repo.InkRepository
import app.folio.data.repo.LibraryRepository
import app.folio.data.repo.NewBook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkPersistenceTest {

    private lateinit var db: FolioDatabase
    private lateinit var ink: InkRepository
    private lateinit var library: LibraryRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FolioDatabase::class.java).build()
        ink = InkRepository(db)
        library = LibraryRepository(db, FileAccess(context), CacheStore(context))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun book(format: BookFormat = BookFormat.PDF) = library.insert(
        NewBook("content://test/ink.${format.name.lowercase()}", "ink", 10, "ink-${format.name}", "application/pdf", format, StorageMode.LINKED, BookMetadata(title = "Ink"), 10),
    )

    @Test
    fun pageStrokesAreSimplifiedAndKeepTheirOpacity() = runTest {
        val bookId = book()
        val line = listOf(InkPoint(0.1f, 0.5f), InkPoint(0.2f, 0.5f), InkPoint(0.3f, 0.5f))
        val saved = ink.addStroke(bookId, page = 4, drawnNoteId = null, tool = InkTool.HIGHLIGHTER, argb = 0x66FDD835, widthNorm = 0.025f, points = line, heightOverWidth = 1.4f)
        assertNotNull(saved)

        val stored = ink.pageStrokes(bookId).first().single()
        assertEquals(4, stored.page)
        assertEquals(InkTool.HIGHLIGHTER, stored.tool)
        assertEquals(0x66FDD835, stored.argb)
        assertEquals(listOf(InkPoint(0.1f, 0.5f), InkPoint(0.3f, 0.5f)), InkPacking.unpack(stored.points))
        assertEquals(stored.id, stored.toInkStroke().id)
    }

    @Test
    fun deletedStrokesCanBeRestoredWithTheSameId() = runTest {
        val bookId = book()
        val saved = ink.addStroke(bookId, 0, null, InkTool.PEN, -0x1000000, 0.006f, listOf(InkPoint(0.5f, 0.5f)), 1f)!!
        val before = ink.strokes(listOf(saved.id))
        ink.deleteStrokes(listOf(saved.id))
        assertTrue(ink.pageStrokes(bookId).first().isEmpty())
        ink.restoreStrokes(before)
        assertEquals(saved.id, ink.pageStrokes(bookId).first().single().id)
    }

    @Test
    fun emptyDrawnNotesAreDiscardedAndFullOnesKept() = runTest {
        val bookId = book(BookFormat.EPUB)
        val empty = ink.createDrawnNote(bookId, BookLocation(totalProgression = 0.2), "20%", 0.2f)
        assertTrue(ink.discardIfEmpty(empty))
        assertNull(ink.observeDrawnNote(empty).first())

        val drawn = ink.createDrawnNote(bookId, BookLocation(totalProgression = 0.3), "30%", 0.3f)
        ink.addStroke(bookId, null, drawn, InkTool.PEN, -0x1000000, 0.006f, listOf(InkPoint(0.1f, 0.1f), InkPoint(0.9f, 0.9f)), 1.4f)
        assertFalse(ink.discardIfEmpty(drawn))
        assertEquals(1, ink.noteStrokes(drawn).first().size)
        assertTrue(ink.pageStrokes(bookId).first().isEmpty())
        assertEquals("Ink", ink.allDrawnNotes.first().single().bookTitle)
    }

    @Test
    fun removingABookRemovesItsInk() = runTest {
        val bookId = book(BookFormat.EPUB)
        val note = ink.createDrawnNote(bookId, BookLocation(), "1%", 0.01f)
        ink.addStroke(bookId, null, note, InkTool.PEN, -0x1000000, 0.006f, listOf(InkPoint(0.2f, 0.2f)), 1.4f)
        ink.addStroke(bookId, 2, null, InkTool.PEN, -0x1000000, 0.006f, listOf(InkPoint(0.2f, 0.2f)), 1.4f)

        // Not LibraryRepository.purge: that also deletes real cover files for this id.
        db.books().delete(listOf(bookId))

        assertTrue(db.ink().allStrokes().isEmpty())
        assertTrue(db.ink().allDrawnNotes().isEmpty())
    }
}
