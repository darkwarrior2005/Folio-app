package app.folio.pomodoro

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.folio.data.db.PomodoroPhase
import app.folio.data.repo.ReadingRepository
import app.folio.data.settings.PomodoroSettings
import app.folio.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.pomodoroStore: DataStore<Preferences> by preferencesDataStore(name = "folio_pomodoro")

@Serializable
data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val running: Boolean = false,
    /** Wall-clock time the current phase ends; survives process death. */
    val endAt: Long = 0,
    /** Remaining time while paused. */
    val pausedRemainingMs: Long = 0,
    val plannedMs: Long = 0,
    val startedAt: Long = 0,
    val completedFocusSessions: Int = 0,
    val bookId: Long? = null,
) {
    val active: Boolean get() = running || pausedRemainingMs > 0

    fun remaining(now: Long): Long = when {
        running -> (endAt - now).coerceAtLeast(0)
        else -> pausedRemainingMs
    }
}

sealed interface PomodoroEvent {
    data class PhaseFinished(val phase: PomodoroPhase, val next: PomodoroPhase) : PomodoroEvent
}

/**
 * The Pomodoro timer. State is persisted as an end timestamp, so a phase that finishes while the
 * app is closed is still recorded and announced when it comes back.
 */
class PomodoroEngine(
    private val context: Context,
    private val settings: SettingsRepository,
    private val reading: ReadingRepository,
    private val scope: CoroutineScope,
) {
    private val store = context.applicationContext.pomodoroStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(PomodoroState())
    val state: StateFlow<PomodoroState> = _state.asStateFlow()

    private val _remaining = MutableStateFlow(0L)
    val remaining: StateFlow<Long> = _remaining.asStateFlow()

    private val _events = MutableSharedFlow<PomodoroEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PomodoroEvent> = _events.asSharedFlow()

    private var ticker: Job? = null

    init {
        scope.launch {
            restore()
            if (_state.value.running) startTicking()
        }
    }

    private suspend fun restore() {
        val raw = store.data.first()[KEY] ?: return
        val restored = runCatching { json.decodeFromString<PomodoroState>(raw) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        if (restored.running && restored.endAt <= now) {
            // The phase ended while the app was away: record it and move on.
            recordPhase(restored, completed = true, endedAt = restored.endAt)
            val next = nextPhase(restored)
            _state.value = restored.copy(
                phase = next,
                running = false,
                endAt = 0,
                pausedRemainingMs = 0,
                completedFocusSessions = restored.completedFocusSessions +
                    if (restored.phase == PomodoroPhase.FOCUS) 1 else 0,
            )
            persist()
            _events.emit(PomodoroEvent.PhaseFinished(restored.phase, next))
        } else {
            _state.value = restored
            _remaining.value = restored.remaining(now)
        }
    }

    suspend fun currentSettings(): PomodoroSettings = settings.current().pomodoro

    fun start(phase: PomodoroPhase? = null, bookId: Long? = null) {
        scope.launch {
            val prefs = currentSettings()
            val target = phase ?: _state.value.phase
            val duration = durationFor(target, prefs)
            val now = System.currentTimeMillis()
            _state.value = _state.value.copy(
                phase = target,
                running = true,
                endAt = now + duration,
                pausedRemainingMs = 0,
                plannedMs = duration,
                startedAt = now,
                bookId = bookId ?: _state.value.bookId,
            )
            persist()
            startTicking()
            PomodoroService.start(context)
        }
    }

    fun pause() {
        val now = System.currentTimeMillis()
        val current = _state.value
        if (!current.running) return
        _state.value = current.copy(running = false, pausedRemainingMs = current.remaining(now), endAt = 0)
        _remaining.value = _state.value.pausedRemainingMs
        ticker?.cancel()
        scope.launch { persist() }
        PomodoroService.stop(context)
    }

    fun resume() {
        val current = _state.value
        if (current.running || current.pausedRemainingMs <= 0) return
        val now = System.currentTimeMillis()
        _state.value = current.copy(running = true, endAt = now + current.pausedRemainingMs, pausedRemainingMs = 0)
        scope.launch { persist() }
        startTicking()
        PomodoroService.start(context)
    }

    fun stop() {
        val current = _state.value
        ticker?.cancel()
        scope.launch {
            if (current.active) {
                recordPhase(current, completed = false, endedAt = System.currentTimeMillis())
            }
            _state.value = PomodoroState(completedFocusSessions = current.completedFocusSessions)
            _remaining.value = 0
            persist()
        }
        PomodoroService.stop(context)
    }

    fun skip() {
        scope.launch { finishPhase(completed = false) }
    }

    fun attachBook(bookId: Long?) {
        _state.value = _state.value.copy(bookId = bookId)
    }

    private fun startTicking() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val current = _state.value
                if (!current.running) break
                val now = System.currentTimeMillis()
                val left = current.remaining(now)
                _remaining.value = left
                if (left <= 0) {
                    finishPhase(completed = true)
                    break
                }
                delay(500)
            }
        }
    }

    private suspend fun finishPhase(completed: Boolean) {
        val current = _state.value
        val prefs = currentSettings()
        recordPhase(current, completed, System.currentTimeMillis())
        val next = nextPhase(current)
        val completedFocus = current.completedFocusSessions +
            if (current.phase == PomodoroPhase.FOCUS && completed) 1 else 0

        _state.value = current.copy(
            phase = next,
            running = false,
            endAt = 0,
            pausedRemainingMs = 0,
            completedFocusSessions = completedFocus,
        )
        _remaining.value = 0
        persist()
        PomodoroService.stop(context)
        _events.emit(PomodoroEvent.PhaseFinished(current.phase, next))

        if (completed) {
            PomodoroNotifications.notifyPhaseFinished(context, current.phase, next, prefs)
        }

        val autoStart = when (next) {
            PomodoroPhase.FOCUS -> prefs.autoStartNextFocus
            else -> prefs.autoStartBreaks
        }
        if (completed && autoStart) start(next)
    }

    private suspend fun recordPhase(state: PomodoroState, completed: Boolean, endedAt: Long) {
        if (state.startedAt <= 0) return
        val actual = (endedAt - state.startedAt).coerceAtLeast(0)
        if (actual < MIN_RECORDED_MS) return
        reading.recordPomodoro(
            phase = state.phase,
            startedAt = state.startedAt,
            plannedMs = state.plannedMs,
            actualMs = actual,
            completed = completed,
            bookId = state.bookId,
        )
    }

    private fun nextPhase(state: PomodoroState): PomodoroPhase = when (state.phase) {
        PomodoroPhase.FOCUS -> PomodoroPhase.SHORT_BREAK
        else -> PomodoroPhase.FOCUS
    }

    private suspend fun durationFor(phase: PomodoroPhase, prefs: PomodoroSettings): Long {
        val minutes = when (phase) {
            PomodoroPhase.FOCUS -> prefs.focusMinutes
            PomodoroPhase.SHORT_BREAK -> {
                val done = _state.value.completedFocusSessions
                if (done > 0 && done % prefs.sessionsBeforeLongBreak == 0) {
                    prefs.longBreakMinutes
                } else {
                    prefs.shortBreakMinutes
                }
            }
            PomodoroPhase.LONG_BREAK -> prefs.longBreakMinutes
        }
        return minutes.coerceAtLeast(1) * 60_000L
    }

    private suspend fun persist() {
        val encoded = json.encodeToString(_state.value)
        store.edit { it[KEY] = encoded }
    }

    companion object {
        private val KEY = stringPreferencesKey("pomodoro_state")
        private const val MIN_RECORDED_MS = 30_000L
    }
}
