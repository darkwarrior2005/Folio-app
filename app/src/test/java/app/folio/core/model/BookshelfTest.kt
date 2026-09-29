package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfTest {

    private val now = 1_800_000_000_000L

    private fun book(
        id: Long,
        added: Long = id,
        status: ReadingStatus = ReadingStatus.UNREAD,
        progress: Float = 0f,
        lastOpened: Long? = null,
        pages: Int? = 200,
        missing: Boolean = false,
    ) = LibraryBook(
        id = id,
        uri = "content://book/$id",
        title = "Book $id",
        author = null,
        series = null,
        volume = null,
        year = null,
        fileName = "book$id.pdf",
        format = BookFormat.PDF,
        status = status,
        favorite = false,
        progress = progress,
        pageCount = pages,
        fileSize = 1_000_000,
        dateAdded = added,
        lastOpenedAt = lastOpened,
        finishedAt = null,
        categoryId = null,
        categoryName = null,
        tags = emptyList(),
        collectionIds = emptySet(),
        coverPath = null,
        missing = missing,
        customOrder = 0,
        sortTitle = "book $id",
    )

    @Test
    fun everyCollectionBecomesAShelfInItsOwnOrder() {
        val books = (1L..5L).map { book(it) }
        val shelf = BookshelfBuilder.build(
            books = books,
            collections = listOf(ShelfCollection(10, "Science"), ShelfCollection(20, "Empty")),
            links = listOf(ShelfLink(10, 3, 1), ShelfLink(10, 1, 0), ShelfLink(10, 5, 2)),
            now = now,
        )
        assertEquals(listOf("Science", "Empty"), shelf.shelves.map { it.name })
        assertEquals(listOf(1L, 3L, 5L), shelf.shelves[0].books.map { it.id })
        assertTrue(shelf.shelves[1].books.isEmpty())
    }

    @Test
    fun booksOnNoShelfAreUnsortedNewestFirst() {
        val books = listOf(book(1, added = 100), book(2, added = 300), book(3, added = 200))
        val shelf = BookshelfBuilder.build(
            books = books,
            collections = listOf(ShelfCollection(10, "Science")),
            links = listOf(ShelfLink(10, 2, 0)),
            now = now,
        )
        assertEquals(listOf(3L, 1L), shelf.unsorted.map { it.id })
    }

    @Test
    fun linksToRemovedBooksOrUnknownCollectionsAreIgnored() {
        val shelf = BookshelfBuilder.build(
            books = listOf(book(1)),
            collections = listOf(ShelfCollection(10, "Science")),
            links = listOf(ShelfLink(10, 99, 0), ShelfLink(77, 1, 0)),
            now = now,
        )
        assertTrue(shelf.shelves.single().books.isEmpty())
        // A link to a collection that no longer exists does not hide the book.
        assertEquals(listOf(1L), shelf.unsorted.map { it.id })
    }

    @Test
    fun readingShelfHoldsStartedBooksMostRecentFirst() {
        val books = listOf(
            book(1, status = ReadingStatus.READING, lastOpened = 10),
            book(2, progress = 0.4f, lastOpened = 30),
            book(3, status = ReadingStatus.FINISHED, progress = 1f, lastOpened = 40),
            book(4),
            book(5, status = ReadingStatus.READING, lastOpened = 50, missing = true),
        )
        val shelf = BookshelfBuilder.build(books, emptyList(), emptyList(), now)
        assertEquals(listOf(2L, 1L), shelf.reading.map { it.id })
    }

    @Test
    fun emptyLibraryIsEmpty() {
        assertTrue(BookshelfBuilder.build(emptyList(), listOf(ShelfCollection(1, "A")), emptyList(), now).isEmpty)
    }

    @Test
    fun looksAreStableAndInRange() {
        (1L..500L).forEach { id ->
            val look = BookLooks.of(book(id))
            assertEquals(look, BookLooks.of(book(id)))
            assertTrue(look.heightFraction in 0.8f..1.0f)
            assertTrue(look.thickness in 0f..1f)
            assertTrue(look.band in 0 until BookLooks.BANDS)
        }
    }

    @Test
    fun thickerBooksHaveMorePages() {
        val slim = BookLooks.of(book(1, pages = 60)).thickness
        val thick = BookLooks.of(book(1, pages = 900)).thickness
        assertTrue(thick > slim)
    }

    @Test
    fun onlySomeBooksLeanAndNeverWhenNotAllowed() {
        val leaning = (1L..700L).count { BookLooks.of(book(it)).tiltDegrees != 0f }
        assertTrue(leaning in 50..160)
        assertTrue((1L..700L).all { BookLooks.of(book(it), leanAllowed = false).tiltDegrees == 0f })
    }
}
