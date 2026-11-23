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
import kotlinx.coroutines.launch

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
    val manga: app.folio.data.repo.MangaRepository by lazy { app.folio.data.repo.MangaRepository(database) }
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

    /**
     * A smart collection the library should show the next time it appears, set by shortcuts that
     * lead into the library (the home bookshelf, the Collections screen). The library clears it.
     */
    val libraryRequest = kotlinx.coroutines.flow.MutableStateFlow<app.folio.core.model.SmartCollection?>(null)

    // Manga sources. Nothing here opens a connection until the user turns manga sources on;
    // see MangaAccess.
    val mangaAccess: app.folio.manga.MangaAccess by lazy { app.folio.manga.MangaAccess(settings) }

    /**
     * The manga settings mirrored for the places that have to read them synchronously: an OkHttp
     * interceptor and a WorkManager constraint builder cannot suspend.
     */
    @Volatile
    private var mangaSettings: app.folio.data.settings.MangaSettings = app.folio.data.settings.MangaSettings()

    val downloadsOverWifiOnly: Boolean get() = mangaSettings.downloadOverWifiOnly

    val sourceNetwork: eu.kanade.tachiyomi.network.NetworkHelper by lazy {
        eu.kanade.tachiyomi.network.NetworkHelper(appContext) {
            mangaSettings.userAgent.ifBlank { eu.kanade.tachiyomi.network.NetworkHelper.DEFAULT_USER_AGENT }
        }
    }

    val sources: app.folio.manga.source.SourceManager by lazy { app.folio.manga.source.SourceManager() }

    /**
     * A manga title handed to the Library's Manga tab to search every source for, e.g. from a
     * manga its own source has no chapters of. Memory only, cleared once picked up.
     */
    val pendingMangaSearch = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** Saved pages, feeds and web novels; it fetches nothing while reading from the web is off. */
    val web: app.folio.web.WebLibrary by lazy {
        app.folio.web.WebLibrary(appContext, settings, imports, library, rescanner, cache)
    }

    /** A web address shared to Folio, waiting for the "From the web" screen. Memory only. */
    val pendingWebUrl = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    val browsingData: app.folio.data.privacy.BrowsingData by lazy {
        app.folio.data.privacy.BrowsingData(appContext, manga) { sourceNetwork }
    }

    private val extensionTrust by lazy { app.folio.manga.extension.ExtensionTrust(database) }

    val extensionRepos: app.folio.manga.extension.ExtensionRepoService by lazy {
        app.folio.manga.extension.ExtensionRepoService(database, sourceNetwork, mangaAccess)
    }

    val extensions: app.folio.manga.extension.ExtensionManager by lazy {
        // Has to be registered before any extension class is instantiated.
        app.folio.manga.MangaSourceModule.install(appContext, sourceNetwork)
        app.folio.manga.extension.ExtensionManager(
            loader = app.folio.manga.extension.ExtensionLoader(appContext, extensionTrust),
            trust = extensionTrust,
            repos = extensionRepos,
            installer = app.folio.manga.extension.ExtensionInstaller(
                appContext,
                sourceNetwork,
                mangaAccess,
                extensionRepos,
            ),
            sources = sources,
            access = mangaAccess,
            settings = settings,
            scope = appScope,
        )
    }

    val downloadProvider: app.folio.manga.download.DownloadProvider by lazy {
        app.folio.manga.download.DownloadProvider(appContext)
    }

    val downloader: app.folio.manga.download.Downloader by lazy {
        app.folio.manga.download.Downloader(database, sources, downloadProvider, settings, mangaAccess)
    }

    val downloads: app.folio.manga.download.DownloadManager by lazy {
        app.folio.manga.download.DownloadManager(appContext, database, downloadProvider, mangaAccess, downloader)
    }

    val mangaChapters: app.folio.manga.reader.MangaChapterLoader by lazy {
        app.folio.manga.reader.MangaChapterLoader(database, downloads, sources, files, cache, mangaAccess)
    }

    val backup: app.folio.data.backup.BackupRepository by lazy {
        app.folio.data.backup.BackupRepository(database, settings, files, cache, library)
    }

    init {
        // Mirrors the manga settings for the callers that cannot suspend; see mangaSettings.
        appScope.launch { settings.settings.collect { mangaSettings = it.manga } }
    }
}
