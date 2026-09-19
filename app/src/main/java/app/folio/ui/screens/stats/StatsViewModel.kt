package app.folio.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.LibraryQuery
import app.folio.core.model.ReadingStatus
import app.folio.core.model.StatsCalculator
import app.folio.data.db.GoalType
import app.folio.data.db.ReadingGoalEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LibraryStats(
    val totalBooks: Int = 0,
    val finished: Int = 0,
    val reading: Int = 0,
    val unread: Int = 0,
    val totalTimeMs: Long = 0,
    val pagesRead: Int = 0,
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val finishedThisMonth: Int = 0,
    val finishedThisYear: Int = 0,
    val averageSessionMs: Long = 0,
    val sessionCount: Int = 0,
    val lastSevenDays: List<Pair<LocalDate, Long>> = emptyList(),
    val lastThirtyDays: List<Pair<LocalDate, Long>> = emptyList(),
    val topCategories: List<Pair<String, Long>> = emptyList(),
    val todayMs: Long = 0,
    val weekMs: Long = 0,
)

class StatsViewModel(private val container: AppContainer) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    val goals: StateFlow<List<ReadingGoalEntity>> = container.reading.goals
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<LibraryStats> =
        combine(
            container.library.libraryBooks,
            container.reading.samples,
        ) { books, samples ->
            val totals = StatsCalculator.dailyTotals(samples, zone)
            val today = LocalDate.now(zone)
            val monthStart = today.withDayOfMonth(1)
            val yearStart = today.withDayOfYear(1)
            val categoryById = books.associate { it.id to it.categoryName }

            LibraryStats(
                totalBooks = books.size,
                finished = books.count { it.status == ReadingStatus.FINISHED },
                reading = books.count {
                    it.status == ReadingStatus.READING ||
                        (it.progress > 0f && it.progress < LibraryQuery.FINISHED_PROGRESS)
                },
                unread = books.count { it.status == ReadingStatus.UNREAD && it.progress == 0f },
                totalTimeMs = samples.sumOf { it.durationMs },
                pagesRead = samples.sumOf { it.pagesRead },
                streak = StatsCalculator.currentStreak(totals, today),
                longestStreak = StatsCalculator.longestStreak(totals),
                finishedThisMonth = books.count { book ->
                    book.finishedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() >= monthStart } == true
                },
                finishedThisYear = books.count { book ->
                    book.finishedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() >= yearStart } == true
                },
                averageSessionMs = StatsCalculator.averageSessionMs(samples),
                sessionCount = samples.size,
                lastSevenDays = StatsCalculator.lastNDays(totals, today, 7),
                lastThirtyDays = StatsCalculator.lastNDays(totals, today, 30),
                topCategories = StatsCalculator.shareBy(samples) { sample ->
                    sample.bookId?.let { categoryById[it] }
                }.take(5),
                todayMs = totals[today] ?: 0L,
                weekMs = StatsCalculator.totalBetween(totals, today.minusDays(6), today),
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryStats())

    fun setGoal(type: GoalType, target: Int, enabled: Boolean) = viewModelScope.launch {
        container.reading.setGoal(type, target, enabled)
    }
}
