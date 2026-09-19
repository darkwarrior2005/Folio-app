package app.folio.data.backup

import app.folio.data.db.BookMusicEntity
import app.folio.data.db.BookMusicSelectionEntity
import app.folio.data.db.BookMusicSourceEntity
import app.folio.data.db.MusicCollectionEntity
import app.folio.data.db.MusicCollectionTrackEntity
import app.folio.data.db.MusicTagEntity
import app.folio.data.db.TrackEntity
import app.folio.data.db.TrackTagEntity
import androidx.room.withTransaction
import app.folio.data.db.BookEntity
import app.folio.data.db.BookTagEntity
import app.folio.data.db.BookmarkEntity
import app.folio.data.db.CategoryEntity
import app.folio.data.db.CollectionBookEntity
import app.folio.data.db.CollectionEntity
import app.folio.data.db.DrawnNoteEntity
import app.folio.data.db.FolioDatabase
import app.folio.data.db.HighlightEntity
import app.folio.data.db.InkStrokeEntity
import app.folio.data.db.NoteEntity
import app.folio.data.db.PomodoroSessionEntity
import app.folio.data.db.QueueItemEntity
import app.folio.data.db.ReadingGoalEntity
import app.folio.data.db.ReadingProgressEntity
import app.folio.data.db.ReadingSessionEntity
import app.folio.data.db.TagEntity
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.repo.LibraryRepository
import app.folio.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupDocument(
    val version: Int = VERSION,
    val createdAt: Long,
    val books: List<BookEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val collectionBooks: List<CollectionBookEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val bookTags: List<BookTagEntity> = emptyList(),
    val progress: List<ReadingProgressEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val highlights: List<HighlightEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val sessions: List<ReadingSessionEntity> = emptyList(),
    val pomodoros: List<PomodoroSessionEntity> = emptyList(),
    val goals: List<ReadingGoalEntity> = emptyList(),
    val queue: List<QueueItemEntity> = emptyList(),
    val tracks: List<TrackEntity> = emptyList(),
    val musicTags: List<MusicTagEntity> = emptyList(),
    val trackTags: List<TrackTagEntity> = emptyList(),
    val musicCollections: List<MusicCollectionEntity> = emptyList(),
    val musicCollectionTracks: List<MusicCollectionTrackEntity> = emptyList(),
    val bookMusic: List<BookMusicEntity> = emptyList(),
    val bookMusicSources: List<BookMusicSourceEntity> = emptyList(),
    val bookMusicSelections: List<BookMusicSelectionEntity> = emptyList(),
    val drawnNotes: List<DrawnNoteEntity> = emptyList(),
    val inkStrokes: List<InkStrokeEntity> = emptyList(),
    val settings: String? = null,
) {
    companion object {
        const val VERSION = 1
    }
}

data class BackupSummary(val books: Int, val annotations: Int, val bytes: Long)

data class RestoreSummary(val books: Int, val missingFiles: Int, val annotations: Int)

/**
 * Backups are a plain ZIP: library.json plus the cover images (and optionally the book files).
 * JSON means the data stays readable and portable even without this app.
 */
class BackupRepository(
    private val db: FolioDatabase,
    private val settings: SettingsRepository,
    private val files: FileAccess,
    private val cache: CacheStore,
    private val library: LibraryRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // One transaction: without it, a write landing between two of these reads (e.g. a stroke
    // saved right after its drawn note is read) could produce a backup with a stroke pointing at
    // a drawn note the snapshot never captured.
    suspend fun snapshot(): BackupDocument = withContext(Dispatchers.IO) {
        db.withTransaction {
            BackupDocument(
                createdAt = System.currentTimeMillis(),
                books = db.books().all(),
                categories = db.categories().all(),
                collections = db.collections().all(),
                collectionBooks = db.collections().allLinks(),
                tags = db.tags().all(),
                bookTags = db.tags().allLinks(),
                progress = db.reading().allProgress(),
                bookmarks = db.annotations().allBookmarks(),
                highlights = db.annotations().allHighlights(),
                notes = db.annotations().allNotes(),
                sessions = db.reading().allSessions(),
                pomodoros = db.reading().allPomodoros(),
                goals = db.reading().goals(),
                queue = db.reading().queue(),
                tracks = db.music().allTracks(),
                musicTags = db.music().allTags(),
                trackTags = db.music().allTrackTags(),
                musicCollections = db.music().allCollections(),
                musicCollectionTracks = db.music().allCollectionLinks(),
                bookMusic = db.music().allBookMusic(),
                bookMusicSources = db.music().allSources(),
                bookMusicSelections = db.music().allSelections(),
                drawnNotes = db.ink().allDrawnNotes(),
                inkStrokes = db.ink().allStrokes(),
                settings = settings.exportJson(),
            )
        }
    }

    suspend fun writeBackup(targetUri: String, includeBookFiles: Boolean): Result<BackupSummary> =
        withContext(Dispatchers.IO) {
            try {
                val document = snapshot()
                val output = files.openOutputStream(targetUri)
                    ?: return@withContext Result.failure(IllegalStateException("cannot write"))
                var bytes = 0L
                output.use { stream ->
                    ZipOutputStream(stream.buffered()).use { zip ->
                        zip.putNextEntry(ZipEntry(LIBRARY_ENTRY))
                        val payload = json.encodeToString(document).toByteArray()
                        zip.write(payload)
                        bytes += payload.size
                        zip.closeEntry()

                        cache.coversDir.listFiles()?.forEach { cover ->
                            zip.putNextEntry(ZipEntry("$COVERS_DIR${cover.name}"))
                            cover.inputStream().use { it.copyTo(zip) }
                            bytes += cover.length()
                            zip.closeEntry()
                        }

                        if (includeBookFiles) {
                            document.books.forEach { book ->
                                val input = files.openInput(book.uri) ?: return@forEach
                                zip.putNextEntry(ZipEntry("$BOOKS_DIR${book.id}_${book.fileName}"))
                                input.use { bytes += it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
                Result.success(
                    BackupSummary(
                        books = document.books.size,
                        annotations = document.bookmarks.size + document.highlights.size + document.notes.size + document.drawnNotes.size,
                        bytes = bytes,
                    ),
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun restore(sourceUri: String): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        try {
            var document: BackupDocument? = null
            val restoredBooks = mutableMapOf<Long, File>()

            files.openInput(sourceUri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when {
                            entry.name == LIBRARY_ENTRY ->
                                document = json.decodeFromString<BackupDocument>(zip.readBytes().decodeToString())

                            entry.name.startsWith(COVERS_DIR) && !entry.isDirectory -> {
                                val target = File(cache.coversDir, entry.name.removePrefix(COVERS_DIR))
                                target.outputStream().use { zip.copyTo(it) }
                            }

                            entry.name.startsWith(BOOKS_DIR) && !entry.isDirectory -> {
                                val name = entry.name.removePrefix(BOOKS_DIR)
                                val bookId = name.substringBefore('_').toLongOrNull()
                                val target = File(cache.importedDir, name.substringAfter('_'))
                                target.outputStream().use { zip.copyTo(it) }
                                if (bookId != null) restoredBooks[bookId] = target
                            }
                        }
                        entry = zip.nextEntry
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("cannot read"))

            val backup = document ?: return@withContext Result.failure(IllegalStateException("no library.json"))

            db.withTransaction {
                db.ink().deleteAllStrokes()
                db.ink().deleteAllDrawnNotes()
                db.search().deleteAll()
                db.reading().clearQueue()
                db.reading().deleteAllGoals()
                db.reading().deleteAllPomodoros()
                db.reading().deleteAllSessions()
                db.reading().deleteAllProgress()
                db.annotations().deleteAllNotes()
                db.annotations().deleteAllHighlights()
                db.annotations().deleteAllBookmarks()
                db.collections().deleteAll()
                db.tags().deleteAll()
                db.music().deleteAllBookMusic()
                db.music().deleteAllCollections()
                db.music().deleteAllTags()
                db.music().deleteAllTracks()
                db.books().deleteAll()
                db.categories().deleteAll()

                db.categories().let { dao -> backup.categories.forEach { dao.insert(it) } }
                db.books().insertAll(
                    backup.books.map { book ->
                        val restoredFile = restoredBooks[book.id]
                        val coverPath = coverPathFor(book.id, custom = false)
                        val customCover = coverPathFor(book.id, custom = true)
                        book.copy(
                            uri = restoredFile?.let { android.net.Uri.fromFile(it).toString() } ?: book.uri,
                            storage = if (restoredFile != null) {
                                app.folio.core.model.StorageMode.COPIED
                            } else {
                                book.storage
                            },
                            importedCoverPath = coverPath ?: book.importedCoverPath,
                            customCoverPath = customCover ?: book.customCoverPath,
                            indexState = app.folio.core.model.IndexState.PENDING,
                        )
                    },
                )
                db.collections().let { dao ->
                    backup.collections.forEach { dao.insert(it) }
                    dao.addLinks(backup.collectionBooks)
                }
                db.tags().let { dao ->
                    backup.tags.forEach { dao.insert(it) }
                    dao.link(backup.bookTags)
                }
                db.reading().upsertProgress(backup.progress)
                db.annotations().insertBookmarks(backup.bookmarks)
                db.annotations().insertHighlights(backup.highlights)
                db.annotations().insertNotes(backup.notes)
                db.ink().insertDrawnNotes(backup.drawnNotes)
                db.ink().insertStrokes(backup.inkStrokes)
                db.reading().insertSessions(backup.sessions)
                db.reading().insertPomodoros(backup.pomodoros)
                backup.goals.forEach { db.reading().upsertGoal(it) }
                db.reading().enqueueAll(backup.queue)
                db.music().let { dao ->
                    backup.tracks.forEach { track ->
                        val cover = cache.trackCoverFile(track.id).takeIf { it.exists() }?.absolutePath
                        dao.insertTrack(track.copy(coverPath = cover))
                    }
                    backup.musicTags.forEach { dao.insertTag(it) }
                    dao.linkTags(backup.trackTags)
                    backup.musicCollections.forEach { dao.insertCollection(it) }
                    dao.addToCollection(backup.musicCollectionTracks)
                    backup.bookMusic.forEach { dao.upsertBookMusic(it) }
                    dao.insertSources(backup.bookMusicSources)
                    dao.insertSelection(backup.bookMusicSelections)
                }
            }

            backup.settings?.let { settings.importJson(it) }

            val missing = library.refreshAvailability()
            Result.success(
                RestoreSummary(
                    books = backup.books.size,
                    missingFiles = missing,
                    annotations = backup.bookmarks.size + backup.highlights.size + backup.notes.size + backup.drawnNotes.size,
                ),
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun coverPathFor(bookId: Long, custom: Boolean): String? =
        cache.coverFile(bookId, custom).takeIf { it.exists() }?.absolutePath

    /** Markdown export, which is what research notes are most useful in. */
    suspend fun exportAnnotations(targetUri: String, bookId: Long?): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val books = db.books().all().filter { bookId == null || it.id == bookId }.associateBy { it.id }
            val highlights = db.annotations().allHighlights().filter { it.bookId in books.keys }
            val notes = db.annotations().allNotes().filter { it.bookId in books.keys }
            val bookmarks = db.annotations().allBookmarks().filter { it.bookId in books.keys }
            val drawings = db.ink().allDrawnNotes().filter { it.bookId in books.keys }

            val markdown = buildString {
                books.values.sortedBy { it.displayTitle }.forEach { book ->
                    val bookHighlights = highlights.filter { it.bookId == book.id }.sortedBy { it.progress }
                    val bookNotes = notes.filter { it.bookId == book.id }.sortedBy { it.progress }
                    val bookMarks = bookmarks.filter { it.bookId == book.id }.sortedBy { it.progress }
                    val bookDrawings = drawings.filter { it.bookId == book.id }.sortedBy { it.progress }
                    if (bookHighlights.isEmpty() && bookNotes.isEmpty() && bookMarks.isEmpty() && bookDrawings.isEmpty()) return@forEach

                    append("# ").append(book.displayTitle).append('\n')
                    book.displayAuthor?.let { append('*').append(it).append("*\n") }
                    append('\n')

                    if (bookHighlights.isNotEmpty()) {
                        append("## Highlights\n\n")
                        bookHighlights.forEach { highlight ->
                            append("> ").append(highlight.text.replace("\n", "\n> ")).append('\n')
                            append("<sub>").append(highlight.positionLabel).append(" · ")
                                .append(highlight.color.name.lowercase()).append("</sub>\n")
                            highlight.note?.let { append('\n').append(it).append('\n') }
                            append('\n')
                        }
                    }
                    if (bookNotes.isNotEmpty()) {
                        append("## Notes\n\n")
                        bookNotes.forEach { note ->
                            append("- **").append(note.positionLabel).append("** — ")
                                .append(note.text.replace("\n", " ")).append('\n')
                        }
                        append('\n')
                    }
                    if (bookMarks.isNotEmpty()) {
                        append("## Bookmarks\n\n")
                        bookMarks.forEach { bookmark ->
                            append("- ").append(bookmark.title ?: bookmark.positionLabel)
                            bookmark.note?.let { append(" — ").append(it) }
                            append('\n')
                        }
                        append('\n')
                    }
                    if (bookDrawings.isNotEmpty()) {
                        append("## Drawings\n\n")
                        bookDrawings.forEach { drawing ->
                            append("- **").append(drawing.positionLabel).append("** — [drawing]\n")
                        }
                        append('\n')
                    }
                }
            }

            val output = files.openOutputStream(targetUri)
                ?: return@withContext Result.failure(IllegalStateException("cannot write"))
            output.use { it.write(markdown.toByteArray()) }
            Result.success(highlights.size + notes.size + bookmarks.size + drawings.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val LIBRARY_ENTRY = "library.json"
        private const val COVERS_DIR = "covers/"
        private const val BOOKS_DIR = "books/"
    }
}
