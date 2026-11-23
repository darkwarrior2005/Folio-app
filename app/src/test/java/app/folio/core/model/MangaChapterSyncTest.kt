package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaChapterSyncTest {

    private fun source(url: String, name: String = url, order: Int = 0, number: Float = -1f, upload: Long = 0) =
        SourceChapter(url = url, name = name, chapterNumber = number, dateUpload = upload, sourceOrder = order)

    private fun existing(
        id: Long,
        url: String,
        name: String = url,
        order: Int = 0,
        number: Float = -1f,
        upload: Long = 0,
    ) = ExistingChapter(id = id, url = url, name = name, chapterNumber = number, dateUpload = upload, sourceOrder = order)

    @Test
    fun `first sync inserts every chapter`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c1", order = 0), source("/c2", order = 1)),
            existing = emptyList(),
        )

        assertEquals(listOf("/c1", "/c2"), diff.toInsert.map { it.url })
        assertTrue(diff.toUpdate.isEmpty())
        assertTrue(diff.toDelete.isEmpty())
    }

    @Test
    fun `unchanged chapters are left alone`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c1", name = "Chapter 1", order = 0, number = 1f)),
            existing = listOf(existing(1, "/c1", name = "Chapter 1", order = 0, number = 1f)),
        )

        assertTrue(diff.toInsert.isEmpty())
        assertTrue(diff.toUpdate.isEmpty())
        assertTrue(diff.toDelete.isEmpty())
    }

    @Test
    fun `a new chapter at the top renumbers the rest`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c2", order = 0), source("/c1", order = 1)),
            existing = listOf(existing(1, "/c1", order = 0)),
        )

        assertEquals(listOf("/c2"), diff.toInsert.map { it.url })
        // The chapter that moved down keeps its id and read state, only its order changes.
        assertEquals(listOf(1L), diff.toUpdate.map { it.first })
        assertEquals(1, diff.toUpdate.single().second.sourceOrder)
        assertTrue(diff.toDelete.isEmpty())
    }

    @Test
    fun `a renamed chapter is updated rather than replaced`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c1", name = "Chapter 1 - The End")),
            existing = listOf(existing(7, "/c1", name = "Chapter 1")),
        )

        assertTrue(diff.toInsert.isEmpty())
        assertEquals(7L, diff.toUpdate.single().first)
        assertEquals("Chapter 1 - The End", diff.toUpdate.single().second.name)
    }

    @Test
    fun `a chapter the source dropped is deleted`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c1")),
            existing = listOf(existing(1, "/c1"), existing(2, "/c2")),
        )

        assertEquals(listOf(2L), diff.toDelete)
    }

    @Test
    fun `an unknown upload date does not overwrite a recorded one`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c1", upload = 0)),
            existing = listOf(existing(1, "/c1", upload = 1_000L)),
        )

        assertTrue(diff.toUpdate.isEmpty())
    }

    @Test
    fun `a corrected upload date is applied`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c1", upload = 2_000L)),
            existing = listOf(existing(1, "/c1", upload = 1_000L)),
        )

        assertEquals(2_000L, diff.toUpdate.single().second.dateUpload)
    }

    @Test
    fun `a changed url counts as a different chapter`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c1-v2", name = "Chapter 1")),
            existing = listOf(existing(1, "/c1", name = "Chapter 1")),
        )

        assertEquals(listOf("/c1-v2"), diff.toInsert.map { it.url })
        assertEquals(listOf(1L), diff.toDelete)
    }

    @Test
    fun `a url the source lists twice is inserted once`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c2", name = "Chapter 2", order = 0), source("/c1", order = 1), source("/c2", name = "Chapter 2 (again)", order = 2)),
            existing = emptyList(),
        )

        assertEquals(listOf("/c2", "/c1"), diff.toInsert.map { it.url })
        assertEquals("Chapter 2", diff.toInsert.first().name)
    }

    @Test
    fun `a new memo is applied but a missing one keeps the stored memo`() {
        val stored = ExistingChapter(id = 1, url = "/c1", name = "/c1", memo = "{\"id\":\"a\"}")

        val changed = MangaChapterSync.diff(listOf(SourceChapter(url = "/c1", name = "/c1", memo = "{\"id\":\"b\"}")), listOf(stored))
        assertEquals("{\"id\":\"b\"}", changed.toUpdate.single().second.memo)

        val missing = MangaChapterSync.diff(listOf(SourceChapter(url = "/c1", name = "/c1")), listOf(stored))
        assertTrue(missing.toUpdate.isEmpty())
    }

    @Test
    fun `an empty list from the source is refused instead of deleting everything`() {
        val kept = runCatching { MangaChapterSync.diff(source = emptyList(), existing = listOf(existing(1, "/c1"))) }
        assertTrue((kept.exceptionOrNull() as NoChaptersException).hadChapters)

        val none = runCatching { MangaChapterSync.diff(source = emptyList(), existing = emptyList()) }
        assertTrue(!(none.exceptionOrNull() as NoChaptersException).hadChapters)
    }

    @Test
    fun `an undated new chapter takes the newest date seen before it, else now`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c3"), source("/c2", upload = 500L), source("/c1")),
            existing = emptyList(),
            now = 9_000L,
        )
        assertEquals(listOf(9_000L, 500L, 500L), diff.toInsert.map { it.dateUpload })
    }

    @Test
    fun `a chapter back under a new url keeps the read and bookmark marks of its number`() {
        val diff = MangaChapterSync.diff(
            source = listOf(source("/c5-new", number = 5f), source("/c6", number = 6f)),
            existing = listOf(
                ExistingChapter(id = 1, url = "/c5-old", name = "/c5-old", chapterNumber = 5f, read = true, bookmark = true),
            ),
        )
        val back = diff.toInsert.single { it.url == "/c5-new" }
        assertTrue(back.read && back.bookmark)
        assertTrue(!diff.toInsert.single { it.url == "/c6" }.read)
        assertEquals(listOf(1L), diff.toDelete)
    }

    @Test
    fun `prepare cleans names and recognises numbers like Mihon`() {
        val prepared = MangaChapterSync.prepare(
            "Bleach",
            listOf(
                SourceChapter(url = "/2", name = "Bleach - Chapter 2: Second", scanlator = "  Team  "),
                SourceChapter(url = "/1", name = "Bleach - Chapter 1", scanlator = " "),
                SourceChapter(url = "/1", name = "duplicate"),
            ),
        )
        assertEquals(listOf("Chapter 2: Second", "Chapter 1"), prepared.map { it.name })
        assertEquals(listOf(2f, 1f), prepared.map { it.chapterNumber })
        assertEquals(listOf("Team", null), prepared.map { it.scanlator })
    }

    @Test
    fun `prepare keeps a number the source gave`() {
        val prepared = MangaChapterSync.prepare("", listOf(SourceChapter(url = "/x", name = "Chapter 3", chapterNumber = 7f)))
        assertEquals(7f, prepared.single().chapterNumber)
    }
}
