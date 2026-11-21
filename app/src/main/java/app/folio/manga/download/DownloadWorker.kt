package app.folio.manga.download

import android.app.Notification
import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.folio.FolioApp
import app.folio.R

/**
 * Runs the download queue as expedited WorkManager work, so it survives the app being swapped
 * away. Distinguishes "queue empty" from "stopped by WorkManager" (a network/battery constraint
 * no longer holding) so the latter retries instead of silently giving up.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT < 31) {
            setForegroundAsync(foregroundInfo())
        }
        val container = (applicationContext as? FolioApp)?.container ?: return Result.success()
        val finishedCleanly = runQueue(container)
        return if (finishedCleanly) Result.success() else Result.retry()
    }

    /** @return true if the queue genuinely ran dry, false if WorkManager is asking us to stop. */
    private suspend fun runQueue(container: app.folio.AppContainer): Boolean {
        val downloadManager = container.downloadManager
        val downloader = container.downloader
        while (!isStopped) {
            val next = downloadManager.queue.value.firstOrNull { it.progress !is DownloadProgress.Done } ?: return true
            val source = container.sourceManager.get(next.sourceId) as? eu.kanade.tachiyomi.source.online.HttpSource
                ?: continue
            val sChapter = eu.kanade.tachiyomi.source.model.SChapter.create().apply {
                url = next.chapter.url
                name = next.chapter.name
            }
            try {
                downloader.downloadChapter(source, next.mangaTitle, next.chapter, sChapter) { progress ->
                    downloadManager.updateProgress(next.chapter.id, progress)
                }
            } catch (_: Exception) {
                downloadManager.updateProgress(next.chapter.id, DownloadProgress.Failed("Download failed"))
            }
        }
        return false
    }

    private fun foregroundInfo(): ForegroundInfo {
        val channelId = "manga_downloads"
        val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager?.createNotificationChannel(
                android.app.NotificationChannel(channelId, "Manga downloads", android.app.NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = Notification.Builder(applicationContext, channelId)
            .setContentTitle("Downloading chapters")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 42
        const val WORK_NAME = "folio-manga-downloads"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }

        /** Resumes a queue a killed process left behind; call from FolioApp.onCreate(). */
        fun resumeIfNeeded(context: Context) = schedule(context)
    }
}
