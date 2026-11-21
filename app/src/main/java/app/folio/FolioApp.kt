package app.folio

import android.app.Application
import app.folio.importer.IndexWorker
import app.folio.manga.source.SourceCoverCallFactory
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.launch

class FolioApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    private val fallbackClient by lazy { okhttp3.OkHttpClient() }

    override fun newImageLoader(context: android.content.Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { SourceCoverCallFactory(container.sourceManager, fallbackClient) },
                    ),
                )
            }
            .build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        uy.kohesive.injekt.Injekt.importModule(
            app.folio.manga.MangaSourceModule(this, container.settings),
        )
        container.bookMusic.start()
        app.folio.manga.download.DownloadWorker.resumeIfNeeded(this)

        container.appScope.launch {
            // Files move and disappear outside the app; find out before the user taps one.
            container.library.refreshAvailability()
            val settings = container.settings.current()
            container.library.purgeExpiredTrash(settings.storage.trashRetentionDays)
            if (settings.storage.fullTextIndexing) {
                IndexWorker.schedule(this@FolioApp)
            }
            if (settings.privacy.forgetBrowsing) {
                app.folio.data.privacy.BrowsingData(this@FolioApp, container).forget(includeSiteData = false)
            }
        }
    }
}
