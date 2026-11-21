package app.folio.ui.nav

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val NOTES = "notes"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    const val SEARCH = "search"
    const val COLLECTIONS = "collections"
    const val CATEGORIES = "categories"
    const val TAGS = "tags"
    const val QUEUE = "queue"
    const val TRASH = "trash"
    const val POMODORO = "pomodoro"
    const val THEMES = "settings/themes"
    const val READER_SETTINGS = "settings/reader"
    const val LIBRARY_SETTINGS = "settings/library"
    const val POMODORO_SETTINGS = "settings/pomodoro"
    const val STORAGE = "settings/storage"
    const val PRIVACY = "settings/privacy"
    const val ACCESSIBILITY = "settings/accessibility"
    const val NOTIFICATIONS = "settings/notifications"
    const val BACKUP = "settings/backup"
    const val ABOUT = "settings/about"
    const val NIGHT_LIGHT = "settings/night-light"

    const val BOOK_ID = "bookId"
    const val COLLECTION_ID = "collectionId"

    const val MUSIC = "music"
    const val TRACK_ID = "trackId"
    const val TRACK_EDIT = "music/track/{$TRACK_ID}"
    const val MUSIC_COLLECTION = "music/collection/{$COLLECTION_ID}"
    fun trackEdit(id: Long) = "music/track/$id"
    fun musicCollection(id: Long) = "music/collection/$id"

    const val NOTE_ID = "noteId"
    const val DRAWN_NOTE = "drawing/{$NOTE_ID}"
    fun drawnNote(id: Long) = "drawing/$id"

    /** Saved-state key: the reader jumps to this location once it comes back into view. */
    const val READER_JUMP = "readerJump"

    private const val BOOK_DETAILS_BASE = "book"
    private const val EDIT_BOOK_BASE = "book/edit"
    private const val READER_BASE = "reader"
    private const val COLLECTION_BASE = "collection"

    const val BOOK_DETAILS = "$BOOK_DETAILS_BASE/{$BOOK_ID}"
    const val EDIT_BOOK = "$EDIT_BOOK_BASE/{$BOOK_ID}"
    const val READER = "$READER_BASE/{$BOOK_ID}?location={location}"
    const val COLLECTION_DETAIL = "$COLLECTION_BASE/{$COLLECTION_ID}"

    fun bookDetails(id: Long) = "$BOOK_DETAILS_BASE/$id"
    fun editBook(id: Long) = "$EDIT_BOOK_BASE/$id"
    fun reader(id: Long) = "$READER_BASE/$id"

    /** Opening straight at a bookmark, highlight or search hit. */
    fun readerAt(id: Long, location: String) =
        "$READER_BASE/$id?location=${android.net.Uri.encode(location)}"

    fun collection(id: Long) = "$COLLECTION_BASE/$id"

    val bottomBar = listOf(HOME, LIBRARY, NOTES, STATS, SETTINGS)

    const val MANGA_BROWSE = "manga/browse"
    const val SOURCE_ID = "sourceId"
    const val MANGA_SOURCE = "manga/source/{$SOURCE_ID}"
    const val MANGA_ID = "mangaId"
    const val MANGA_DETAILS = "manga/details/{$MANGA_ID}"
    const val CHAPTER_ID = "chapterId"
    const val MANGA_READER = "manga/reader/{$MANGA_ID}/{$CHAPTER_ID}"
    const val MANGA_DOWNLOADS = "manga/downloads"
    const val MANGA_SETTINGS = "settings/manga"
    const val PACKAGE_NAME = "packageName"
    const val MANGA_SOURCE_SETTINGS = "manga/source-settings/{$PACKAGE_NAME}"
    const val EXTENSIONS_TAB = "tab"
    const val MANGA_EXTENSIONS = "manga/extensions?$EXTENSIONS_TAB={$EXTENSIONS_TAB}"

    fun mangaSource(id: Long) = "manga/source/$id"
    fun mangaDetails(id: Long) = "manga/details/$id"
    fun mangaReader(mangaId: Long, chapterId: Long) = "manga/reader/$mangaId/$chapterId"
    fun mangaSourceSettings(packageName: String) = "manga/source-settings/${android.net.Uri.encode(packageName)}"
    fun mangaExtensions(tab: Int) = "manga/extensions?$EXTENSIONS_TAB=$tab"

    const val SOURCE_URL = "url"
    const val SOURCE_NAME = "sourceName"
    const val MANGA_SOURCE_WEBVIEW = "manga/webview/{$SOURCE_ID}?$SOURCE_URL={$SOURCE_URL}"
    fun mangaSourceWebView(sourceId: Long, url: String) =
        "manga/webview/$sourceId?$SOURCE_URL=${android.net.Uri.encode(url)}"
}
