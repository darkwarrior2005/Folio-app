package app.folio

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.folio.importer.IndexWorker
import app.folio.manga.download.DownloadWorker
import app.folio.manga.extension.ExtensionInstallReceiver
import app.folio.manga.source.SourceCoverCallFactory
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class FolioApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    /**
     * Images load as before, except manga covers, which go through the source they came from
     * (its client and headers), as Mihon loads them.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            SourceCoverCallFactory(
                                sources = { container.sources },
                                fallback = { plainClient },
                            )
                        },
                    ),
                )
            }
            .build()

    private val plainClient by lazy { OkHttpClient() }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.bookMusic.start()
        // Follows the manga sources setting; loads nothing while it is off.
        container.extensions.start()
        // Since Android 8 these broadcasts no longer reach receivers declared in the manifest, so
        // without this an extension installed or removed outside Folio showed up only once the
        // Extensions screen was opened again.
        ContextCompat.registerReceiver(
            this,
            ExtensionInstallReceiver(),
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        container.appScope.launch {
            // Files move and disappear outside the app; find out before the user taps one.
            container.library.refreshAvailability()
            val settings = container.settings.current()
            // A fresh start forgets what the last session browsed: manga only looked at, cached
            // site responses and covers. Nothing the user added or downloaded is touched.
            if (settings.privacy.forgetBrowsing) container.browsingData.forget()
            container.library.purgeExpiredTrash(settings.storage.trashRetentionDays)
            if (settings.storage.fullTextIndexing) {
                IndexWorker.schedule(this@FolioApp)
            }
            // A queue interrupted by the process dying (not by WorkManager retrying it) would
            // otherwise sit at QUEUED/DOWNLOADING forever with nothing to nudge it again.
            if (container.downloads.observeQueueSize().first() > 0) {
                DownloadWorker.start(this@FolioApp)
            }
        }
    }
}
