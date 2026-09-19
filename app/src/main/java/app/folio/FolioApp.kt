package app.folio

import android.app.Application
import app.folio.importer.IndexWorker
import kotlinx.coroutines.launch

class FolioApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.bookMusic.start()

        container.appScope.launch {
            // Files move and disappear outside the app; find out before the user taps one.
            container.library.refreshAvailability()
            val settings = container.settings.current()
            container.library.purgeExpiredTrash(settings.storage.trashRetentionDays)
            if (settings.storage.fullTextIndexing) {
                IndexWorker.schedule(this@FolioApp)
            }
        }
    }
}
