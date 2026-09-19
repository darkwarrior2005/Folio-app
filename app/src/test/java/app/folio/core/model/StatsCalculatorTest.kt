package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsCalculatorTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 9, 15)

    private fun session(day: LocalDate, minutes: Long, pages: Int = 0) = SessionSample(
        bookId = 1,
        startedAt = day.atTime(20, 0).toInstant(zone).toEpochMilli(),
        durationMs = minutes * 60_000,
        pagesRead = pages,
    )

    @Test
    fun streakCountsConsecutiveDaysEndingToday() {
        val sessions = listOf(session(today, 10), session(today.minusDays(1), 5), session(today.minusDays(2), 30))
        val totals = StatsCalculator.dailyTotals(sessions, zone)
        assertEquals(3, StatsCalculator.currentStreak(totals, today))
    }

    @Test
    fun streakSurvivesUntilMidnightWhenTodayIsEmpty() {
        val sessions = listOf(session(today.minusDays(1), 5), session(today.minusDays(2), 5))
        assertEquals(2, StatsCalculator.currentStreak(StatsCalculator.dailyTotals(sessions, zone), today))
    }

    @Test
    fun gapBreaksStreakAndTinySessionsDoNotCount() {
        val sessions = listOf(session(today, 10), session(today.minusDays(2), 10), session(today.minusDays(1), 0))
        val totals = StatsCalculator.dailyTotals(sessions, zone)
        assertEquals(1, StatsCalculator.currentStreak(totals, today))
        assertEquals(1, StatsCalculator.longestStreak(totals))
    }

    @Test
    fun lastNDaysIsZeroFilledOldestFirst() {
        val totals = StatsCalculator.dailyTotals(listOf(session(today, 42)), zone)
        val week = StatsCalculator.lastNDays(totals, today, 7)
        assertEquals(7, week.size)
        assertEquals(today.minusDays(6), week.first().first)
        assertEquals(42 * 60_000L, week.last().second)
        assertEquals(0L, week.first().second)
    }

    @Test
    fun averagesAndShares() {
        val sessions = listOf(session(today, 10), session(today, 30))
        assertEquals(20 * 60_000L, StatsCalculator.averageSessionMs(sessions))
        val share = StatsCalculator.shareBy(sessions) { if (it.durationMs > 15 * 60_000) "long" else "short" }
        assertEquals("long", share.first().first)
    }
}
