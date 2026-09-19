package app.folio.core.model

sealed interface PlaybackSource {
    data object Library : PlaybackSource
    data class Book(val bookId: Long) : PlaybackSource
}

data class BookMusicPlan(val bookId: Long, val family: ReaderFamily, val hasMusic: Boolean, val autoplay: Boolean)

sealed interface SwitchDecision {
    data object Keep : SwitchDecision
    data class PlayBook(val bookId: Long) : SwitchDecision
}

sealed interface PomodoroMusicAction {
    data object None : PomodoroMusicAction
    data object Pause : PomodoroMusicAction
    data object Resume : PomodoroMusicAction
}

/** "Follow the book": the only place that decides when reading changes what is playing. */
object MusicSwitchPolicy {

    fun supports(family: ReaderFamily): Boolean = family == ReaderFamily.PDF || family == ReaderFamily.EPUB

    fun onBookOpened(current: PlaybackSource?, plan: BookMusicPlan): SwitchDecision = when {
        !supports(plan.family) || !plan.hasMusic || !plan.autoplay -> SwitchDecision.Keep
        current == PlaybackSource.Book(plan.bookId) -> SwitchDecision.Keep
        else -> SwitchDecision.PlayBook(plan.bookId)
    }

    fun onPomodoroPhase(isBreak: Boolean, enabled: Boolean, playing: Boolean, pausedByPomodoro: Boolean): PomodoroMusicAction =
        when {
            !enabled -> PomodoroMusicAction.None
            isBreak && playing -> PomodoroMusicAction.Pause
            !isBreak && pausedByPomodoro -> PomodoroMusicAction.Resume
            else -> PomodoroMusicAction.None
        }
}
