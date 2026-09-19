package app.folio.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.LibraryBook
import app.folio.core.model.LibraryQuery
import app.folio.core.model.ReadingStatus
import app.folio.core.model.StatsCalculator
import app.folio.data.db.CollectionWithCount
import app.folio.data.db.GoalType
import app.folio.data.settings.AppSettings
import app.folio.pomodoro.PomodoroState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId

data class HomeStats(
    val todayMs: Long = 0,
    val weekMs: Long = 0,
    val streak: Int = 0,
    val goalMinutes: Int? = null,
    val goalProgress: Float = 0f,
    val lastSevenDays: List<Pair<LocalDate, Long>> = emptyList(),
    val finishedThisMonth: Int = 0,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    fun import(uris: List<String>) = container.imports.enqueue(uris)

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val books = container.library.libraryBooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val continueReading: StateFlow<List<LibraryBook>> = books
        .map { list ->
            list.filter {
                !it.missing &&
                    (it.status == ReadingStatus.READING || (it.progress > 0f && it.progress < LibraryQuery.FINISHED_PROGRESS))
            }
                .sortedByDescending { it.lastOpenedAt ?: 0 }
                .take(12)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyAdded: StateFlow<List<LibraryBook>> = books
        .map { list -> list.sortedByDescending { it.dateAdded }.take(12) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val collections: StateFlow<List<CollectionWithCount>> = container.organization.collections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val queue: StateFlow<List<LibraryBook>> =
        combine(container.organization.queue, books) { queue, library ->
            val byId = library.associateBy { it.id }
            queue.sortedBy { it.position }.mapNotNull { byId[it.bookId] }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<HomeStats> =
        combine(container.reading.samples, container.reading.goals, books) { samples, goals, library ->
            val totals = StatsCalculator.dailyTotals(samples, zone)
            val today = LocalDate.now(zone)
            val minutesGoal = goals.firstOrNull { it.type == GoalType.MINUTES_PER_DAY && it.enabled }
            val todayMs = totals[today] ?: 0L
            val monthStart = today.withDayOfMonth(1)
            HomeStats(
                todayMs = todayMs,
                weekMs = StatsCalculator.totalBetween(totals, today.minusDays(6), today),
                streak = StatsCalculator.currentStreak(totals, today),
                goalMinutes = minutesGoal?.target,
                goalProgress = minutesGoal?.let {
                    (todayMs.toFloat() / (it.target * 60_000f)).coerceIn(0f, 1f)
                } ?: 0f,
                lastSevenDays = StatsCalculator.lastNDays(totals, today, 7),
                finishedThisMonth = library.count { book ->
                    book.finishedAt?.let { finished ->
                        java.time.Instant.ofEpochMilli(finished).atZone(zone).toLocalDate() >= monthStart
                    } == true
                },
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats())

    val pomodoro: StateFlow<PomodoroState> = container.pomodoro.state
    val pomodoroRemaining: StateFlow<Long> = container.pomodoro.remaining

    fun startFocus() = container.pomodoro.start(app.folio.data.db.PomodoroPhase.FOCUS)

    fun pausePomodoro() = container.pomodoro.pause()

    fun resumePomodoro() = container.pomodoro.resume()

    fun stopPomodoro() = container.pomodoro.stop()
}
