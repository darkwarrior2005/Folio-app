package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicQueueBuilderTest {

    private val collections = mapOf(10L to listOf(1L, 2L, 3L), 20L to listOf(3L, 4L))

    @Test
    fun attachedTracksFollowSourceOrderWithoutDuplicates() {
        val sources = listOf(MusicSourceRef(10L, null), MusicSourceRef(null, 5L), MusicSourceRef(20L, null))
        val result = MusicQueueBuilder.attachedTracks(sources, collections, available = setOf(1, 2, 3, 4, 5))
        assertEquals(listOf(1L, 2L, 3L, 5L, 4L), result)
    }

    @Test
    fun missingTracksAreDropped() {
        val sources = listOf(MusicSourceRef(10L, null))
        assertEquals(listOf(1L, 3L), MusicQueueBuilder.attachedTracks(sources, collections, available = setOf(1, 3)))
    }

    @Test
    fun loopKeepsAttachedOrder() {
        assertEquals(listOf(1L, 2L, 3L), MusicQueueBuilder.forBook(BookMusicMode.LOOP, listOf(1, 2, 3), emptyList(), seed = 7))
    }

    @Test
    fun shuffleIsAStablePermutation() {
        val attached = (1L..20L).toList()
        val first = MusicQueueBuilder.forBook(BookMusicMode.SHUFFLE, attached, emptyList(), seed = 42)
        val again = MusicQueueBuilder.forBook(BookMusicMode.SHUFFLE, attached, emptyList(), seed = 42)
        assertEquals(first, again)
        assertEquals(attached.toSet(), first.toSet())
        assertNotEquals(attached, first)
    }

    @Test
    fun selectionUsesSelectionOrderAndOnlyAttachedTracks() {
        val result = MusicQueueBuilder.forBook(BookMusicMode.SELECTION, listOf(1, 2, 3), listOf(3, 9, 1), seed = 0)
        assertEquals(listOf(3L, 1L), result)
    }

    @Test
    fun emptySelectionMeansNothingToPlay() {
        assertTrue(MusicQueueBuilder.forBook(BookMusicMode.SELECTION, listOf(1, 2), emptyList(), seed = 0).isEmpty())
    }
}
