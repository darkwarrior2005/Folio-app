package app.folio

import android.content.Context
import app.folio.data.db.FolioDatabase
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.repo.AnnotationRepository
import app.folio.data.repo.LibraryRepository
import app.folio.data.repo.OrganizationRepository
import app.folio.data.repo.ReadingRepository
import app.folio.data.repo.SearchRepository
import app.folio.data.settings.SettingsRepository
import app.folio.importer.ImportManager
import app.folio.importer.MetadataRescanner
import app.folio.reader.ReaderRegistry
import app.folio.reader.comic.ComicEngine
import app.folio.reader.epub.EpubEngine
import app.folio.reader.epub.ReadiumStack
import app.folio.reader.pdf.PdfEngine
import app.folio.reader.text.TextEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. Everything is local: a database file, the user's own book files,
 * a cache directory and the reader engines.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: FolioDatabase by lazy { FolioDatabase.build(appContext) }
    val files: FileAccess by lazy { FileAccess(appContext) }
    val cache: CacheStore by lazy { CacheStore(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }

    val library: LibraryRepository by lazy { LibraryRepository(database, files, cache) }
    val organization: OrganizationRepository by lazy { OrganizationRepository(database) }
    val annotations: AnnotationRepository by lazy { AnnotationRepository(database) }
    val ink: app.folio.data.repo.InkRepository by lazy { app.folio.data.repo.InkRepository(database) }
    val reading: ReadingRepository by lazy { ReadingRepository(database) }
    val search: SearchRepository by lazy { SearchRepository(database) }

    val music: app.folio.data.repo.MusicRepository by lazy {
        app.folio.data.repo.MusicRepository(database, files, cache)
    }

    val musicPlayer: app.folio.music.MusicPlayer by lazy {
        app.folio.music.MusicPlayer(appContext, music, appScope)
    }

    val readium: ReadiumStack by lazy { ReadiumStack(appContext) }

    val readers: ReaderRegistry by lazy {
        ReaderRegistry(
            listOf(
                EpubEngine(readium),
                PdfEngine(files),
                ComicEngine(files, cache),
                TextEngine(files),
            ),
        )
    }

    val imports: ImportManager by lazy {
        ImportManager(library, files, cache, readers, settings, appScope)
    }

    val rescanner: MetadataRescanner by lazy { MetadataRescanner(library, readers) }

    val pomodoro: app.folio.pomodoro.PomodoroEngine by lazy {
        app.folio.pomodoro.PomodoroEngine(appContext, settings, reading, appScope)
    }

    val bookMusic: app.folio.music.BookMusicCoordinator by lazy {
        app.folio.music.BookMusicCoordinator(music, musicPlayer, pomodoro, settings, appScope)
    }

    val backup: app.folio.data.backup.BackupRepository by lazy {
        app.folio.data.backup.BackupRepository(database, settings, files, cache, library)
    }
}
