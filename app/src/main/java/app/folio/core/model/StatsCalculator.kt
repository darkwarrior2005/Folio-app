package app.folio.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SessionSample(
    val bookId: Long?,
    val startedAt: Long,
    val durationMs: Long,
    val pagesRead: Int,
)

/** Pure statistics over reading sessions. Days are attributed by session start time. */
object StatsCalculator {

    /** Minimum reading on a day for it to count towards a streak. */
    const val STREAK_MIN_MS = 60_000L

    fun dailyTotals(sessions: List<SessionSample>, zone: ZoneId): Map<LocalDate, Long> =
        sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.durationMs } }

    fun dailyPages(sessions: List<SessionSample>, zone: ZoneId): Map<LocalDate, Int> =
        sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sumOf { it.pagesRead } }

    /**
     * Consecutive reading days ending today. If today has no reading yet, a streak that ended
     * yesterday is still alive (the user has until midnight).
     */
    fun currentStreak(totals: Map<LocalDate, Long>, today: LocalDate): Int {
        fun readOn(d: LocalDate) = (totals[d] ?: 0L) >= STREAK_MIN_MS
        var day = if (readOn(today)) today else today.minusDays(1)
        var streak = 0
        while (readOn(day)) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    fun longestStreak(totals: Map<LocalDate, Long>): Int {
        val days = totals.filterValues { it >= STREAK_MIN_MS }.keys.sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** Oldest first, [n] entries including today, zero-filled. */
    fun lastNDays(totals: Map<LocalDate, Long>, today: LocalDate, n: Int): List<Pair<LocalDate, Long>> =
        (n - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            d to (totals[d] ?: 0L)
        }

    fun totalBetween(totals: Map<LocalDate, Long>, fromInclusive: LocalDate, toInclusive: LocalDate): Long =
        totals.filterKeys { !it.isBefore(fromInclusive) && !it.isAfter(toInclusive) }.values.sum()

    fun averageSessionMs(sessions: List<SessionSample>): Long =
        if (sessions.isEmpty()) 0L else sessions.sumOf { it.durationMs } / sessions.size

    /** Reading time per key (e.g. category), largest first. */
    fun <K> shareBy(sessions: List<SessionSample>, keyOf: (SessionSample) -> K?): List<Pair<K, Long>> =
        sessions.mapNotNull { s -> keyOf(s)?.let { it to s.durationMs } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
            .toList()
            .sortedByDescending { it.second }
}
