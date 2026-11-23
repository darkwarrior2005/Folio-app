package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterSelectionTest {

    /** Ten chapters listed newest first, as sources list them: ids 10..1 are chapters 10..1. */
    private fun chapters(
        read: Set<Long> = emptySet(),
        downloaded: Set<Long> = emptySet(),
        queued: Set<Long> = emptySet(),
        bookmarked: Set<Long> = emptySet(),
    ) = (10L downTo 1L).map { id ->
        ChapterFacts(
            id = id,
            read = id in read,
            downloaded = id in downloaded,
            queued = id in queued,
            bookmark = id in bookmarked,
            number = id.toFloat(),
        )
    }

    @Test
    fun `next unread starts after the last chapter read and skips what is already downloaded`() {
        // Read 1-3, skipped 4, read 5; 6 is already downloaded, 7 is queued.
        val list = chapters(read = setOf(1, 2, 3, 5), downloaded = setOf(6), queued = setOf(7))
        assertEquals(listOf(8L, 9L, 10L), ChapterSelection.nextUnreadToDownload(list, 5))
        assertEquals(listOf(8L), ChapterSelection.nextUnreadToDownload(list, 1))
    }

    @Test
    fun `next unread with nothing read starts at the first chapter`() {
        assertEquals(listOf(1L, 2L, 3L), ChapterSelection.nextUnreadToDownload(chapters(), 3))
    }

    @Test
    fun `conditions pick the matching chapters`() {
        val list = chapters(read = setOf(1, 2), downloaded = setOf(2, 3), queued = setOf(4), bookmarked = setOf(9))
        assertEquals(setOf(1L, 2L), ChapterSelection.matching(list, ChapterCondition.READ))
        assertEquals((3L..10L).toSet(), ChapterSelection.matching(list, ChapterCondition.UNREAD))
        assertEquals(setOf(2L, 3L), ChapterSelection.matching(list, ChapterCondition.DOWNLOADED))
        // A queued chapter is on its way, so it does not count as still to download.
        assertEquals(setOf(1L, 5L, 6L, 7L, 8L, 9L, 10L), ChapterSelection.matching(list, ChapterCondition.NOT_DOWNLOADED))
        assertEquals(setOf(9L), ChapterSelection.matching(list, ChapterCondition.BOOKMARKED))
    }

    @Test
    fun `a range covers both ends whichever was picked first`() {
        val list = chapters()
        assertEquals(setOf(3L, 4L, 5L, 6L), ChapterSelection.between(list, 3, 6))
        assertEquals(setOf(3L, 4L, 5L, 6L), ChapterSelection.between(list, 6, 3))
        assertEquals(emptySet<Long>(), ChapterSelection.between(list, 3, 99))
    }

    @Test
    fun `a number range ignores chapters without a number`() {
        val list = chapters() + ChapterFacts(id = 11, read = false, downloaded = false, queued = false, bookmark = false, number = -1f)
        assertEquals(setOf(2L, 3L, 4L), ChapterSelection.numbered(list, 4f, 2f))
    }

    @Test
    fun `invert flips the selection`() {
        assertEquals((1L..8L).toSet(), ChapterSelection.inverted(chapters(), setOf(9, 10)))
    }

    @Test
    fun `downloading a selection skips what is already there and goes in reading order`() {
        val list = chapters(downloaded = setOf(2), queued = setOf(3))
        assertEquals(listOf(1L, 4L, 5L), ChapterSelection.toDownload(list, setOf(5, 4, 3, 2, 1)))
    }
}
