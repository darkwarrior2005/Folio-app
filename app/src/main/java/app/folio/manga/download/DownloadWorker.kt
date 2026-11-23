package app.folio.manga.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.folio.FolioApp
import app.folio.R

/**
 * Runs the download queue outside the reader's lifetime, so downloads carry on while the user
 * reads something else or leaves the app, and resume after a restart.
 *
 * The queue lives in the database, so this worker holds no state of its own: it drains whatever
 * is queued and exits.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? FolioApp)?.container ?: return Result.success()

        // Switched off while the work was waiting: drop it rather than retrying.
        if (!container.mangaAccess.enabled()) return Result.success()

        val finished = container.downloader.runQueue(isStopped = { isStopped })
        return if (finished) Result.success() else Result.retry()
    }

    /**
     * Lets a large queue ask to run expedited instead of being the exact scenario WorkManager's
     * execution budget cuts off mid-batch. Pre-Android 12 this means a foreground service, which
     * needs a notification to show while it runs.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.manga_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.manga_downloads))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NAME = "manga-downloads"
        private const val CHANNEL_ID = "folio_manga_downloads"
        private const val NOTIFICATION_ID = 4201

        /**
         * Queues a run, keeping any run already in flight: the worker picks up whatever is in the
         * queue by the time it gets there, so a second request would only duplicate work.
         */
        fun start(context: Context) {
            val container = (context.applicationContext as? FolioApp)?.container
            val wifiOnly = container?.downloadsOverWifiOnly ?: true

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .build(),
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun stop(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(NAME)
        }
    }
}
