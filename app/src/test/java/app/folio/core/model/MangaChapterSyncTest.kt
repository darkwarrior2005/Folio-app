package app.folio.core.model

import eu.kanade.tachiyomi.source.model.SChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaChapterSyncTest {

    private fun sChapter(url: String, name: String, number: Float = -1f, date: Long = 0, scanlator: String? = null): SChapter =
        SChapter.create().apply {
            this.url = url
            this.name = name
            this.chapter_number = number
            this.date_upload = date
            this.scanlator = scanlator
        }

    @Test
    fun emptyFetchedListRefusesInsteadOfWiping() {
        val existing = listOf(MangaChapter(id = 1, mangaId = 1, url = "/c1", name = "Chapter 1"))
        val ex = assertThrows(NoChaptersException::class.java) {
            MangaChapterSync.sync("Manga", existing, emptyList(), now = 1000)
        }
        assertTrue(ex.hadChapters)
    }

    @Test
    fun emptyFetchedListOnAnEmptyLibraryReportsNoChaptersFound() {
        val ex = assertThrows(NoChaptersException::class.java) {
            MangaChapterSync.sync("Manga", emptyList(), emptyList(), now = 1000)
        }
        assertTrue(!ex.hadChapters)
    }

    @Test
    fun duplicateUrlsAreDropped() {
        val fetched = listOf(
            sChapter("/c1", "Chapter 1", number = 1f),
            sChapter("/c1", "Chapter 1 (dup)", number = 1f),
        )
        val result = MangaChapterSync.sync("Manga", emptyList(), fetched, now = 1000)
        assertEquals(1, (result.toInsert + result.toUpdate).size)
    }

    @Test
    fun readAndBookmarkMarksAreKeptByUrl() {
        val existing = listOf(
            MangaChapter(id = 5, mangaId = 1, url = "/c1", name = "Chapter 1", chapterNumber = 1f, read = true, bookmark = true, lastPageRead = 7),
        )
        val fetched = listOf(sChapter("/c1", "Chapter 1 - renamed", number = 1f))
        val result = MangaChapterSync.sync("Manga", existing, fetched, now = 1000)
        val updated = result.toUpdate.single()
        assertEquals(5L, updated.id)
        assertTrue(updated.read)
        assertTrue(updated.bookmark)
        assertEquals(7, updated.lastPageRead)
    }

    @Test
    fun marksFollowTheChapterNumberWhenTheUrlChanges() {
        val existing = listOf(
            MangaChapter(id = 9, mangaId = 1, url = "/old-url", name = "Chapter 3", chapterNumber = 3f, read = true),
        )
        // The source moved to a new URL scheme; chapter 3 is no longer at /old-url.
        val fetched = listOf(sChapter("/new-url-3", "Chapter 3", number = 3f))
        val result = MangaChapterSync.sync("Manga", existing, fetched, now = 1000)
        val updated = (result.toInsert + result.toUpdate).single()
        assertTrue(updated.read)
        assertEquals("/new-url-3", updated.url)
    }

    @Test
    fun undatedNewChapterGetsTheNewestDateSeenBeforeIt() {
        val fetched = listOf(
            sChapter("/c2", "Chapter 2", number = 2f, date = 2000),
            sChapter("/c1extra", "Chapter 1.5", number = 1.5f, date = 0),
        )
        val result = MangaChapterSync.sync("Manga", emptyList(), fetched, now = 999)
        val extra = result.toInsert.first { it.url == "/c1extra" }
        assertEquals(2000L, extra.dateUpload)
    }

    @Test
    fun undatedNewChapterWithNothingBeforeItGetsNow() {
        val fetched = listOf(sChapter("/c1", "Chapter 1", number = 1f, date = 0))
        val result = MangaChapterSync.sync("Manga", emptyList(), fetched, now = 4242)
        assertEquals(4242L, result.toInsert.single().dateUpload)
    }

    @Test
    fun chaptersGoneFromTheSourceAreDeleted() {
        val existing = listOf(
            MangaChapter(id = 1, mangaId = 1, url = "/c1", name = "Chapter 1", chapterNumber = 1f),
            MangaChapter(id = 2, mangaId = 1, url = "/c2", name = "Chapter 2", chapterNumber = 2f),
        )
        val fetched = listOf(sChapter("/c1", "Chapter 1", number = 1f))
        val result = MangaChapterSync.sync("Manga", existing, fetched, now = 1000)
        assertEquals(listOf(2L), result.toDeleteIds)
    }
}
