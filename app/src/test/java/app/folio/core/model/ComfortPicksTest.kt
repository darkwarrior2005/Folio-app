package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class ComfortPicksTest {

    private fun book(
        id: Long,
        progress: Float = 0.3f,
        lastOpenedAt: Long? = null,
        finishedAt: Long? = null,
        missing: Boolean = false,
    ) = LibraryBook(
        id = id, uri = "file://$id", title = "Book $id", author = null, series = null, volume = null,
        year = null, fileName = "$id.epub", format = BookFormat.EPUB, status = ReadingStatus.READING,
        favorite = false, progress = progress, pageCount = 100, fileSize = 0, dateAdded = 0,
        lastOpenedAt = lastOpenedAt, finishedAt = finishedAt, categoryId = null, categoryName = null,
        tags = emptyList(), collectionIds = emptySet(), coverPath = null, missing = missing,
        customOrder = 0, sortTitle = "book $id",
    )

    @Test
    fun resumePicksTheMostRecentlyOpenedInProgressBook() {
        val books = listOf(
            book(1, lastOpenedAt = 100),
            book(2, lastOpenedAt = 300),
            book(3, lastOpenedAt = 200),
        )
        assertEquals(2L, ComfortPicks.resume(books)?.id)
    }

    @Test
    fun resumeIgnoresFinishedAndMissingBooks() {
        val books = listOf(
            book(1, lastOpenedAt = 500, finishedAt = 10),
            book(2, lastOpenedAt = 400, missing = true),
            book(3, lastOpenedAt = 100),
        )
        assertEquals(3L, ComfortPicks.resume(books)?.id)
    }

    @Test
    fun resumeIsNullWithNoInProgressBooks() {
        assertNull(ComfortPicks.resume(listOf(book(1, progress = 0f))))
    }

    @Test
    fun surprisePrefersTheReadingQueueInOrder() {
        val books = listOf(book(1), book(2), book(3, lastOpenedAt = 10))
        val pick = ComfortPicks.surprise(books, queueBookIds = listOf(2, 1), current = book(3))
        assertEquals(2L, pick?.id)
    }

    @Test
    fun surpriseNeverReturnsTheCurrentBook() {
        val current = book(1, lastOpenedAt = 999)
        val books = listOf(current)
        assertNull(ComfortPicks.surprise(books, queueBookIds = listOf(1), current = current))
    }

    @Test
    fun surpriseFallsBackToARandomUnfinishedBookWhenQueueIsEmpty() {
        val books = listOf(book(1), book(2), book(3))
        val pick = ComfortPicks.surprise(books, queueBookIds = emptyList(), random = Random(0), current = null)
        assertNotNull(pick)
    }
}
