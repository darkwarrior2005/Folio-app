package app.folio.pomodoro

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.folio.MainActivity
import app.folio.R
import app.folio.data.db.PomodoroPhase
import app.folio.data.settings.PomodoroSettings

object PomodoroNotifications {

    const val CHANNEL_TIMER = "folio_pomodoro"
    const val CHANNEL_REMINDERS = "folio_reminders"
    const val ONGOING_ID = 4101
    const val FINISHED_ID = 4102
    const val REMINDER_ID = 4103

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIMER,
                context.getString(R.string.pomodoro_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { setShowBadge(false) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun canNotify(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    fun notifyPhaseFinished(
        context: Context,
        finished: PomodoroPhase,
        next: PomodoroPhase,
        settings: PomodoroSettings,
    ) {
        if (settings.vibration) vibrate(context)
        if (!settings.notifications || !canNotify(context)) return
        ensureChannels(context)

        val title = context.getString(
            if (finished == PomodoroPhase.FOCUS) R.string.pomodoro_focus_done else R.string.pomodoro_break_done,
        )
        val body = context.getString(
            if (next == PomodoroPhase.FOCUS) R.string.pomodoro_time_to_read else R.string.pomodoro_time_to_break,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setDefaults(if (settings.sound) NotificationCompat.DEFAULT_SOUND else 0)
            .setContentIntent(openAppIntent(context))
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(FINISHED_ID, notification) }
    }

    fun readingReminder(context: Context, title: String, body: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(REMINDER_ID, notification) }
    }

    fun openAppIntent(context: Context): android.app.PendingIntent =
        android.app.PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        } ?: return
        runCatching {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 120, 220), -1))
        }
    }
}
