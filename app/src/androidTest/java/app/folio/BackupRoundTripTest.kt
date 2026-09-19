package app.folio

import app.folio.core.model.BookMusicMode
import app.folio.core.model.TrackMetadata
import app.folio.data.db.BookMusicSourceEntity
import app.folio.data.repo.BookMusicSettings
import app.folio.data.repo.MusicRepository
import app.folio.data.repo.NewTrack
import org.junit.Assert.assertTrue
import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.folio.core.model.BookFormat
import app.folio.core.model.BookLocation
import app.folio.core.model.BookMetadata
import app.folio.core.model.HighlightColor
import app.folio.core.model.InkPacking
import app.folio.core.model.InkPoint
import app.folio.core.model.InkTool
import app.folio.core.model.StorageMode
import app.folio.data.backup.BackupRepository
import app.folio.data.db.FolioDatabase
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.repo.AnnotationRepository
import app.folio.data.repo.InkRepository
import app.folio.data.repo.LibraryRepository
import app.folio.data.repo.NewBook
import app.folio.data.repo.OrganizationRepository
import app.folio.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private lateinit var db: FolioDatabase
    private lateinit var library: LibraryRepository
    private lateinit var organization: OrganizationRepository
    private lateinit var annotations: AnnotationRepository
    private lateinit var backup: BackupRepository
    private lateinit var music: MusicRepository
    private lateinit var ink: InkRepository
    private lateinit var target: File

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FolioDatabase::class.java).build()
        val files = FileAccess(context)
        val cache = CacheStore(context)
        library = LibraryRepository(db, files, cache)
        organization = OrganizationRepository(db)
        annotations = AnnotationRepository(db)
        backup = BackupRepository(db, SettingsRepository(context), files, cache, library)
        target = File(context.cacheDir, "backup-test.zip")
        music = MusicRepository(db, files, cache)
        ink = InkRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        target.delete()
    }

    @Test
    fun backupAndRestoreKeepsLibraryOrganizationAndNotes() = runTest {
        val bookId = library.insert(
            NewBook(
                uri = "content://test/book.epub",
                fileName = "book.epub",
                fileSize = 2_048,
                fileHash = "hash-b",
                mimeType = "application/epub+zip",
                format = BookFormat.EPUB,
                storage = StorageMode.LINKED,
                imported = BookMetadata(title = "Deep Learning", author = "Ian Goodfellow"),
                pageCount = 775,
            ),
        )
        organization.addTagsToBooks(listOf(bookId), listOf("AI", "Reference"))
        val collectionId = organization.createCollection("My Research")
        organization.addToCollection(collectionId, listOf(bookId))
        library.saveProgress(bookId, BookLocation(page = 41, totalProgression = 0.4), 0.4f, 41, 775)
        annotations.addHighlight(
            bookId = bookId,
            location = BookLocation(page = 41),
            positionLabel = "42",
            progress = 0.4f,
            text = "Backpropagation",
            color = HighlightColor.GREEN,
        )

        val trackId = music.insert(
            NewTrack("content://test/rain.mp3", "rain.mp3", 500, "ht", "audio/mpeg", 30_000, TrackMetadata(title = "Rain"), null),
        )
        val musicCollection = music.createCollection("Study")
        music.addToCollection(musicCollection, listOf(trackId))
        music.saveBookMusic(
            bookId,
            BookMusicSettings(BookMusicMode.LOOP, true, listOf(BookMusicSourceEntity(bookId = bookId, collectionId = musicCollection, position = 0)), emptyList()),
        )

        val drawing = ink.createDrawnNote(bookId, BookLocation(totalProgression = 0.4), "40%", 0.4f)
        ink.addStroke(bookId, null, drawing, InkTool.HIGHLIGHTER, 0x66FDD835, 0.025f, listOf(InkPoint(0.1f, 0.2f), InkPoint(0.8f, 0.2f)), 1.4f)

        val uri = Uri.fromFile(target).toString()
        val written = backup.writeBackup(uri, includeBookFiles = false)
        assertTrue(written.isSuccess)
        assertTrue(target.length() > 0)

        // Wipe everything, as a fresh install would be.
        library.purge(listOf(bookId))
        organization.deleteCollection(collectionId)
        assertEquals(0, library.libraryBooks.first().size)

        val restored = backup.restore(uri)
        assertTrue(restored.isSuccess)

        val books = library.libraryBooks.first()
        assertEquals(1, books.size)
        val book = books.single()
        assertEquals("Deep Learning", book.title)
        assertEquals(2, book.tags.size)
        assertEquals(0.4f, book.progress, 0.001f)
        assertEquals(1, annotations.highlightsOnce(book.id).size)
        assertEquals("My Research", organization.allCollectionsOnce().single().name)
        assertEquals(41, library.position(book.id)?.page)
        assertEquals("Rain", music.tracks.first().single().displayTitle)
        assertTrue(music.hasMusic(book.id))

        val restoredDrawing = ink.allDrawnNotes.first().single()
        assertEquals("40%", restoredDrawing.note.positionLabel)
        val restoredStroke = ink.noteStrokes(restoredDrawing.note.id).first().single()
        assertEquals(0x66FDD835, restoredStroke.argb)
        assertEquals(listOf(InkPoint(0.1f, 0.2f), InkPoint(0.8f, 0.2f)), InkPacking.unpack(restoredStroke.points))
    }
}
