package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicInheritanceTest {

    private fun plan(vararg trackIds: Long, autoplay: Boolean = true) = MusicPlan(
        mode = BookMusicMode.LOOP,
        autoplay = autoplay,
        sources = trackIds.map { MusicSourceRef(collectionId = null, trackId = it) },
    )

    @Test
    fun bookMusicWinsOverItsCollections() {
        val own = plan(1)
        assertEquals(own, MusicInheritance.effective(own, listOf(plan(2), plan(3))))
    }

    @Test
    fun bookWithoutMusicPlaysItsFirstCollectionSoundtrack() {
        val science = plan(2)
        assertEquals(science, MusicInheritance.effective(null, listOf(science, plan(3))))
    }

    @Test
    fun emptyPlansCountAsNoMusic() {
        val history = plan(3, autoplay = false)
        assertEquals(history, MusicInheritance.effective(plan(), listOf(plan(), history)))
    }

    @Test
    fun noMusicAnywhereIsNull() {
        assertNull(MusicInheritance.effective(null, emptyList()))
        assertNull(MusicInheritance.effective(plan(), listOf(plan())))
    }
}
