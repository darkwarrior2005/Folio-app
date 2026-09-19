package app.folio.pomodoro

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.folio.FolioApp
import app.folio.R
import app.folio.data.db.PomodoroPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Keeps the Pomodoro countdown alive and visible while the user reads. The notification uses the
 * system's own countdown chronometer, so it stays accurate without the app waking up each second.
 */
class PomodoroService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = (application as? FolioApp)?.container?.pomodoro
        if (engine == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        PomodoroNotifications.ensureChannels(this)
        startForegroundCompat(buildNotification(engine.state.value))

        watcher?.cancel()
        watcher = scope.launch {
            engine.state.collectLatest { state ->
                if (!state.running) {
                    stopSelf()
                    return@collectLatest
                }
                runCatching {
                    androidx.core.app.NotificationManagerCompat.from(this@PomodoroService)
                        .notify(PomodoroNotifications.ONGOING_ID, buildNotification(state))
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, PomodoroNotifications.ONGOING_ID, notification, type)
        }
    }

    private fun buildNotification(state: PomodoroState): android.app.Notification {
        val title = getString(
            when (state.phase) {
                PomodoroPhase.FOCUS -> R.string.pomodoro_focus
                PomodoroPhase.SHORT_BREAK -> R.string.pomodoro_short_break
                PomodoroPhase.LONG_BREAK -> R.string.pomodoro_long_break
            },
        )
        return NotificationCompat.Builder(this, PomodoroNotifications.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setOngoing(true)
            .setSilent(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(state.endAt)
            .setContentIntent(PomodoroNotifications.openAppIntent(this))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        watcher?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PomodoroService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PomodoroService::class.java)) }
        }
    }
}
