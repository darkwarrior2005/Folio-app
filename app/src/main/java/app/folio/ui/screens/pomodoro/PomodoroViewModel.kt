package app.folio.ui.screens.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.data.db.PomodoroPhase
import app.folio.pomodoro.PomodoroState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class PomodoroViewModel(private val container: AppContainer) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val state: StateFlow<PomodoroState> = container.pomodoro.state
    val remaining: StateFlow<Long> = container.pomodoro.remaining

    private val todaysSessions = container.reading.pomodoros
        .map { sessions ->
            val today = LocalDate.now(zone)
            sessions.filter {
                Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() == today
            }
        }

    val todaySessions: StateFlow<Int> = todaysSessions
        .map { sessions -> sessions.count { it.phase == PomodoroPhase.FOCUS && it.completed } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayFocusMs: StateFlow<Long> = todaysSessions
        .map { sessions -> sessions.filter { it.phase == PomodoroPhase.FOCUS }.sumOf { it.actualMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun start(phase: PomodoroPhase) = container.pomodoro.start(phase)

    fun pause() = container.pomodoro.pause()

    fun resume() = container.pomodoro.resume()

    fun stop() = container.pomodoro.stop()

    fun skip() = container.pomodoro.skip()
}
