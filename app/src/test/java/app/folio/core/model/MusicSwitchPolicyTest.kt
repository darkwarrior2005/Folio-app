package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicSwitchPolicyTest {

    private fun plan(family: ReaderFamily = ReaderFamily.PDF, hasMusic: Boolean = true, autoplay: Boolean = true) =
        BookMusicPlan(bookId = 7, family = family, hasMusic = hasMusic, autoplay = autoplay)

    @Test
    fun openingABookWithMusicSwitchesToIt() {
        assertEquals(SwitchDecision.PlayBook(7), MusicSwitchPolicy.onBookOpened(PlaybackSource.Library, plan()))
        assertEquals(SwitchDecision.PlayBook(7), MusicSwitchPolicy.onBookOpened(null, plan()))
    }

    @Test
    fun reopeningTheSameBookDoesNotRestartItsMusic() {
        assertEquals(SwitchDecision.Keep, MusicSwitchPolicy.onBookOpened(PlaybackSource.Book(7), plan()))
    }

    @Test
    fun booksWithoutMusicOrAutoplayLeaveMusicAlone() {
        assertEquals(SwitchDecision.Keep, MusicSwitchPolicy.onBookOpened(PlaybackSource.Book(3), plan(hasMusic = false)))
        assertEquals(SwitchDecision.Keep, MusicSwitchPolicy.onBookOpened(PlaybackSource.Book(3), plan(autoplay = false)))
    }

    @Test
    fun onlyPdfAndEpubCanHaveMusic() {
        assertTrue(MusicSwitchPolicy.supports(ReaderFamily.PDF))
        assertTrue(MusicSwitchPolicy.supports(ReaderFamily.EPUB))
        assertFalse(MusicSwitchPolicy.supports(ReaderFamily.COMIC))
        assertFalse(MusicSwitchPolicy.supports(ReaderFamily.TEXT))
        assertEquals(SwitchDecision.Keep, MusicSwitchPolicy.onBookOpened(null, plan(family = ReaderFamily.COMIC)))
    }

    @Test
    fun breaksPauseAndFocusResumesOnlyWhatPomodoroPaused() {
        assertEquals(PomodoroMusicAction.Pause, MusicSwitchPolicy.onPomodoroPhase(true, true, playing = true, pausedByPomodoro = false))
        assertEquals(PomodoroMusicAction.Resume, MusicSwitchPolicy.onPomodoroPhase(false, true, playing = false, pausedByPomodoro = true))
        assertEquals(PomodoroMusicAction.None, MusicSwitchPolicy.onPomodoroPhase(false, true, playing = false, pausedByPomodoro = false))
        assertEquals(PomodoroMusicAction.None, MusicSwitchPolicy.onPomodoroPhase(true, enabled = false, playing = true, pausedByPomodoro = false))
    }
}
