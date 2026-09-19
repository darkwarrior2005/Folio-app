package app.folio.pomodoro

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.folio.FolioApp
import app.folio.R
import app.folio.core.model.StatsCalculator
import app.folio.data.db.GoalType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * One local notification a day, only if the user asked for it and only when it is actually useful
 * (nothing read yet, a goal still open, or a streak about to lapse).
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? FolioApp)?.container ?: return Result.success()
        val settings = container.settings.current()
        val notifications = settings.notifications
        if (!notifications.dailyReminder && !notifications.goalReminder && !notifications.streakReminder) {
            return Result.success()
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val samples = container.reading.sessionsOnce().map {
            app.folio.core.model.SessionSample(it.bookId, it.startedAt, it.durationMs, it.pagesRead)
        }
        val totals = StatsCalculator.dailyTotals(samples, zone)
        val todayMs = totals[today] ?: 0L
        val streak = StatsCalculator.currentStreak(totals, today)

        val goals = container.reading.goalsOnce()
        val minutesGoal = goals.firstOrNull { it.type == GoalType.MINUTES_PER_DAY && it.enabled }

        val title = applicationContext.getString(R.string.reminder_title)
        val body = when {
            notifications.streakReminder && streak > 0 && todayMs < StatsCalculator.STREAK_MIN_MS ->
                applicationContext.getString(R.string.reminder_body)
            notifications.goalReminder && minutesGoal != null && todayMs < minutesGoal.target * 60_000L ->
                applicationContext.getString(R.string.reminder_body)
            notifications.dailyReminder && todayMs < StatsCalculator.STREAK_MIN_MS ->
                applicationContext.getString(R.string.reminder_body)
            else -> null
        } ?: return Result.success()

        PomodoroNotifications.readingReminder(applicationContext, title, body)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "folio-reminder"

        fun schedule(context: Context, hour: Int, minute: Int, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val zone = ZoneId.systemDefault()
            val now = LocalDateTime.now(zone)
            var target = now.with(LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59)))
            if (!target.isAfter(now)) target = target.plusDays(1)
            val delay = Duration.between(now, target)

            manager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<ReminderWorker>(Duration.ofDays(1))
                    .setInitialDelay(delay)
                    .build(),
            )
        }
    }
}
