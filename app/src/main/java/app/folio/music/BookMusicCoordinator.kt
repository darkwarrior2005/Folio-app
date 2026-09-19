package app.folio.music

import app.folio.R
import app.folio.core.model.BookMusicPlan
import app.folio.core.model.MusicSwitchPolicy
import app.folio.core.model.PlaybackSource
import app.folio.core.model.PomodoroMusicAction
import app.folio.core.model.ReaderFamily
import app.folio.core.model.SwitchDecision
import app.folio.data.db.PomodoroPhase
import app.folio.data.repo.MusicRepository
import app.folio.data.settings.SettingsRepository
import app.folio.pomodoro.PomodoroEngine
import app.folio.pomodoro.PomodoroEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Applies [MusicSwitchPolicy]: reading a book and Pomodoro phases steer what plays. */
class BookMusicCoordinator(
    private val music: MusicRepository,
    private val player: MusicPlayer,
    private val pomodoro: PomodoroEngine,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val _notices = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val notices: SharedFlow<Int> = _notices.asSharedFlow()

    private var pausedByPomodoro = false

    fun start() {
        scope.launch {
            pomodoro.events.collect { event ->
                if (event !is PomodoroEvent.PhaseFinished) return@collect
                val enabled = settings.settings.first().pomodoro.pauseMusicOnBreaks
                val isBreak = event.next != PomodoroPhase.FOCUS
                val action = MusicSwitchPolicy.onPomodoroPhase(isBreak, enabled, player.state.value.isPlaying, pausedByPomodoro)
                when (action) {
                    PomodoroMusicAction.Pause -> {
                        pausedByPomodoro = true
                        player.pause()
                    }
                    PomodoroMusicAction.Resume -> {
                        pausedByPomodoro = false
                        player.play()
                    }
                    PomodoroMusicAction.None -> Unit
                }
            }
        }
        scope.launch {
            // A user pause or play clears the Pomodoro claim, so we never resume what they paused.
            var wasPlaying = false
            player.state.collect { now ->
                if (now.isPlaying && !wasPlaying) pausedByPomodoro = false
                wasPlaying = now.isPlaying
            }
        }
    }

    fun onBookOpened(bookId: Long, family: ReaderFamily) {
        scope.launch {
            val saved = if (MusicSwitchPolicy.supports(family)) music.bookMusicSettings(bookId) else null
            val plan = BookMusicPlan(
                bookId = bookId,
                family = family,
                hasMusic = saved != null && saved.sources.isNotEmpty(),
                autoplay = saved?.autoplay ?: false,
            )
            val current = player.state.value.source.takeIf { player.state.value.hasQueue }
            if (MusicSwitchPolicy.onBookOpened(current, plan) is SwitchDecision.PlayBook) playBook(bookId)
        }
    }

    fun playBook(bookId: Long) {
        scope.launch {
            val tracks = music.resolveBookQueue(bookId, seed = System.currentTimeMillis())
            if (tracks.isEmpty()) {
                _notices.tryEmit(R.string.music_book_none_found)
                return@launch
            }
            player.playTracks(tracks, PlaybackSource.Book(bookId), fade = true)
        }
    }
}
