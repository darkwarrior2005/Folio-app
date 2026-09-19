package app.folio

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.folio.core.model.BookFormat
import app.folio.core.model.BookMetadata
import app.folio.core.model.BookMusicMode
import app.folio.core.model.StorageMode
import app.folio.core.model.TrackMetadata
import app.folio.data.db.BookMusicSourceEntity
import app.folio.data.db.FolioDatabase
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.repo.BookMusicSettings
import app.folio.data.repo.LibraryRepository
import app.folio.data.repo.MusicRepository
import app.folio.data.repo.NewBook
import app.folio.data.repo.NewTrack
import app.folio.data.repo.TrackEdit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicPersistenceTest {

    private lateinit var db: FolioDatabase
    private lateinit var music: MusicRepository
    private lateinit var library: LibraryRepository
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FolioDatabase::class.java).build()
        music = MusicRepository(db, FileAccess(context), CacheStore(context))
        library = LibraryRepository(db, FileAccess(context), CacheStore(context))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun track(name: String, hash: String) = music.insert(
        NewTrack("content://test/$name", name, 1_000, hash, "audio/mpeg", 60_000, TrackMetadata(title = name), cover = null),
    )

    @Test
    fun editsTagsAndCollectionsRoundTrip() = runTest {
        val a = track("rain.mp3", "h1")
        val b = track("piano.flac", "h2")
        music.applyEdit(a, TrackEdit(title = "Rain on Glass", artist = "Field Recording", album = null, year = 2024, tagNames = listOf("Lo-fi", "lo-fi", "Rain")))

        val edited = music.observeTrack(a).first()!!
        assertEquals("Rain on Glass", edited.displayTitle)
        assertEquals("rain.mp3", edited.imported.title)
        assertEquals(2, music.observeTagsForTrack(a).first().size)

        val collection = music.createCollection("Study")
        music.addToCollection(collection, listOf(a, b))
        music.reorderCollection(collection, listOf(b, a))
        assertEquals(listOf(b, a), music.observeCollectionTracks(collection).first().map { it.id })
    }

    @Test
    fun bookMusicIsSavedAndCascadesWithTheBook() = runTest {
        val a = track("rain.mp3", "h1")
        val collection = music.createCollection("Study")
        music.addToCollection(collection, listOf(a))
        val bookId = library.insert(
            NewBook("content://test/b.pdf", "b.pdf", 10, "hb", "application/pdf", BookFormat.PDF, StorageMode.LINKED, BookMetadata(title = "B"), 10),
        )
        music.saveBookMusic(
            bookId,
            BookMusicSettings(BookMusicMode.SELECTION, autoplay = false, sources = listOf(BookMusicSourceEntity(bookId = bookId, collectionId = collection, position = 0)), selection = listOf(a)),
        )
        assertTrue(music.hasMusic(bookId))
        val saved = music.bookMusicSettings(bookId)!!
        assertEquals(BookMusicMode.SELECTION, saved.mode)
        assertFalse(saved.autoplay)
        assertEquals(listOf(a), saved.selection)

        library.purge(listOf(bookId))
        assertNull(music.bookMusicSettings(bookId))
    }

    @Test
    fun removingATrackClearsCollectionsAndBookMusicButKeepsTheFile() = runTest {
        val file = java.io.File(context.cacheDir, "keep-me.mp3").apply { writeBytes(ByteArray(16)) }
        val a = music.insert(
            NewTrack(android.net.Uri.fromFile(file).toString(), file.name, 16, "hk", "audio/mpeg", 1_000, TrackMetadata(), null),
        )
        val collection = music.createCollection("Study")
        music.addToCollection(collection, listOf(a))
        val bookId = library.insert(
            NewBook("content://test/b.pdf", "b.pdf", 10, "hb", "application/pdf", BookFormat.PDF, StorageMode.LINKED, BookMetadata(title = "B"), 10),
        )
        music.saveBookMusic(
            bookId,
            BookMusicSettings(BookMusicMode.LOOP, true, listOf(BookMusicSourceEntity(bookId = bookId, trackId = a, position = 0)), emptyList()),
        )

        music.removeFromLibrary(listOf(a))

        assertTrue(music.tracks.first().isEmpty())
        assertTrue(music.observeCollectionTracks(collection).first().isEmpty())
        assertFalse(music.hasMusic(bookId))
        assertTrue(file.exists())
        file.delete()
    }

    @Test
    fun deletingTheFileRemovesTrackAndFile() = runTest {
        val file = java.io.File(context.cacheDir, "delete-me.mp3").apply { writeBytes(ByteArray(16)) }
        val a = music.insert(
            NewTrack(android.net.Uri.fromFile(file).toString(), file.name, 16, "hd", "audio/mpeg", 1_000, TrackMetadata(), null),
        )

        assertTrue(music.deleteFileAndForget(a))

        assertFalse(file.exists())
        assertNull(music.observeTrack(a).first())
    }

    @Test
    fun aFileThatCannotBeDeletedKeepsTheTrack() = runTest {
        val a = music.insert(
            NewTrack("content://nowhere/missing.mp3", "missing.mp3", 16, "hm", "audio/mpeg", 1_000, TrackMetadata(), null),
        )

        assertFalse(music.deleteFileAndForget(a))

        assertTrue(music.observeTrack(a).first() != null)
    }

    @Test
    fun duplicateImportIsDetectedByHashAndSize() = runTest {
        track("rain.mp3", "same")
        assertTrue(db.music().findDuplicate("same", 1_000) != null)
    }
}
