package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ComfortPicksTest {

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
    fun `resume picks the most recently opened unfinished book`() {
        val books = listOf(
            book(1, progress = 0.3f, lastOpened = 10),
            book(2, progress = 0.5f, lastOpened = 30),
            book(3, status = ReadingStatus.FINISHED, progress = 1f, lastOpened = 50),
            book(4, progress = 0.2f, lastOpened = 40, missing = true),
        )
        assertEquals(2L, ComfortPicks.resume(books)?.id)
        assertNull(ComfortPicks.resume(listOf(book(5))))
    }

    @Test
    fun `surprise prefers the queue and never offers the current book`() {
        val books = listOf(book(1, progress = 0.4f, lastOpened = 9), book(2), book(3), book(4))
        repeat(20) { seed ->
            val pick = ComfortPicks.surprise(books, queueIds = listOf(1, 3), random = Random(seed))
            assertEquals(3L, pick?.id)
        }
    }

    @Test
    fun `with an empty queue surprise picks an unfinished book`() {
        val books = listOf(book(1, status = ReadingStatus.FINISHED, progress = 1f), book(2), book(3, missing = true))
        repeat(10) { seed -> assertEquals(2L, ComfortPicks.surprise(books, emptyList(), Random(seed))?.id) }
        assertTrue(ComfortPicks.surprise(listOf(book(9, status = ReadingStatus.FINISHED, progress = 1f)), emptyList()) == null)
    }
}
