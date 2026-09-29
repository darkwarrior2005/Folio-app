# Folio 1.5.0

Folio 1.5.0 (versionCode 15) contains every change listed below, applied on top of 1.3.0. The log was written when these changes were numbered 1.4.0; apart from the version number, 1.5.0 is the same code.

## Folio: all changes since 1.3.0, with code

Repository `darkwarrior2005/Folio-app-private`. Changes from `410dcd8` (Folio 1.3.0) to `4cb4e7c` (`main` after PR #5).

## Timeline

| PR | What |
| --- | --- |
| #1 | Bookshelf home screen: shelves from collections, clock linked to Pomodoro, music card, a "Classic" home option, the "Not on a shelf" smart collection, and a fix for smart-collection shortcuts |
| #2 | Release APK workflow, `gradlew` made executable, version 1.4.0 |
| #3 (Copilot) | Workflow uses `android-actions/setup-android@v4` (v3 failed while installing the removed `tools` package) |
| #4 | Docs: the 1.4.0 change log and a copy of the workflow |
| #5 | Other books becomes a bottom shelf (the side column is removed); collection soundtracks (Room v4); the *Reading music* sheet on the home player |

Builds: Release APK run #2 (`main`, PR #3) and run #3 (PR #5 code) both passed their unit tests and the signed release build.

## What the app does now

- **Home is a bookcase.** It shows, top to bottom:
  - **Currently reading** (books face out, with progress).
  - One shelf per **collection**, in collection order, with the name set into the shelf board.
  - **Other books**: always the bottom shelf, holding only books in no collection.
  - Books are drawn as spines or covers from their real cover data. Tap opens the reader, long-press opens details, and tapping a shelf board opens that collection (or the filtered library).
- **Header:** a live clock that opens the Pomodoro screen and shows its countdown, plus Search, Settings and Collections.
- **Music card:** on top of the existing player; tap it for the full player.
- ***Reading music* sheet:** set music for a book or a whole collection, pick playlists and songs, make new playlists, set shuffle and autoplay.
- **Collection soundtracks:** a book without its own music plays its collection's soundtrack (the first of its collections, in shelf order, that has one). Books added later inherit it automatically. Only PDF and EPUB books play reading music.
- **Classic home:** the original home is available in Settings → Library → Home screen.
- **Database:** Room is now version 4. The upgrade only adds tables, and backups include the new data.

## Files changed

| File | Status | +/- |
| --- | --- | --- |
| `.github/workflows/release.yml` | added | +91 / -0 |
| `README.md` | modified | +8 / -1 |
| `app/build.gradle.kts` | modified | +2 / -2 |
| `app/src/androidTest/java/app/folio/MigrationTest.kt` | modified | +35 / -0 |
| `app/src/main/java/app/folio/AppContainer.kt` | modified | +6 / -0 |
| `app/src/main/java/app/folio/core/model/Bookshelf.kt` | added | +114 / -0 |
| `app/src/main/java/app/folio/core/model/LibraryQuery.kt` | modified | +5 / -1 |
| `app/src/main/java/app/folio/core/model/Music.kt` | modified | +19 / -0 |
| `app/src/main/java/app/folio/data/backup/BackupRepository.kt` | modified | +11 / -0 |
| `app/src/main/java/app/folio/data/db/FolioDatabase.kt` | modified | +9 / -2 |
| `app/src/main/java/app/folio/data/db/MusicDao.kt` | modified | +39 / -0 |
| `app/src/main/java/app/folio/data/db/MusicEntities.kt` | modified | +35 / -0 |
| `app/src/main/java/app/folio/data/repo/MusicRepository.kt` | modified | +85 / -8 |
| `app/src/main/java/app/folio/data/settings/AppSettings.kt` | modified | +4 / -0 |
| `app/src/main/java/app/folio/music/BookMusicCoordinator.kt` | modified | +2 / -1 |
| `app/src/main/java/app/folio/ui/FolioRoot.kt` | modified | +12 / -1 |
| `app/src/main/java/app/folio/ui/components/BookCover.kt` | modified | +1 / -1 |
| `app/src/main/java/app/folio/ui/screens/home/Bookshelf.kt` | added | +557 / -0 |
| `app/src/main/java/app/folio/ui/screens/home/HomeHeader.kt` | added | +245 / -0 |
| `app/src/main/java/app/folio/ui/screens/home/HomeMusicCard.kt` | added | +241 / -0 |
| `app/src/main/java/app/folio/ui/screens/home/HomeScreen.kt` | modified | +124 / -0 |
| `app/src/main/java/app/folio/ui/screens/home/HomeViewModel.kt` | modified | +21 / -0 |
| `app/src/main/java/app/folio/ui/screens/home/MusicAssignSheet.kt` | added | +617 / -0 |
| `app/src/main/java/app/folio/ui/screens/library/LibraryScreen.kt` | modified | +1 / -0 |
| `app/src/main/java/app/folio/ui/screens/library/LibraryViewModel.kt` | modified | +8 / -0 |
| `app/src/main/java/app/folio/ui/screens/settings/SettingsScreens.kt` | modified | +20 / -0 |
| `app/src/main/res/values/strings.xml` | modified | +47 / -0 |
| `app/src/test/java/app/folio/core/model/BookshelfTest.kt` | added | +128 / -0 |
| `app/src/test/java/app/folio/core/model/MusicInheritanceTest.kt` | added | +38 / -0 |
| `docs/Folio-1.4.0-changes.md` | added | +2189 / -0 |
| `docs/release.yml` | added | +91 / -0 |
| `gradlew` | mode change (made executable) | +0 / -0 |

## Data model and settings

### 1. `app/src/main/java/app/folio/data/settings/AppSettings.kt`

Adds `HomeStyle` (`BOOKSHELF` by default, or `CLASSIC`) and the `HomeSettings.style` field. Settings saved by 1.3 still load, because missing keys fall back to the defaults. (A left/right `ShelfSide` setting was added in PR #1 and removed again in PR #5.)

Diff:

```diff
@@ -22,6 +22,9 @@ enum class ImportMode { LINK, COPY }
 
 enum class HomeSection { CONTINUE_READING, READING_GOAL, STREAK, RECENTLY_ADDED, COLLECTIONS, ACTIVITY, POMODORO, QUEUE }
 
+/** Bookshelf shows collections as shelves; Classic is the original list of home sections. */
+enum class HomeStyle { BOOKSHELF, CLASSIC }
+
 @Serializable
 data class AppearanceSettings(
     val themeId: String = "paper",
@@ -146,6 +149,7 @@ data class AccessibilitySettings(
 data class HomeSettings(
     val sections: List<HomeSection> = HomeSection.entries.toList(),
     val greeting: Boolean = true,
+    val style: HomeStyle = HomeStyle.BOOKSHELF,
 )
 
 @Serializable
```

### 2. `app/src/main/java/app/folio/core/model/LibraryQuery.kt`

New smart collection `UNSORTED` ("Not on a shelf"): books that are in no collection.

Diff:

```diff
@@ -9,7 +9,10 @@ enum class BookSort { TITLE, AUTHOR, RECENTLY_OPENED, RECENTLY_ADDED, PROGRESS,
 enum class LibraryGrouping { NONE, CATEGORY, SERIES, AUTHOR, STATUS, FORMAT }
 
 enum class SmartCollection {
-    RECENTLY_ADDED, RECENTLY_READ, CURRENTLY_READING, FINISHED, NEVER_OPENED, FAVORITES, LONG_BOOKS, SHORT_READS
+    RECENTLY_ADDED, RECENTLY_READ, CURRENTLY_READING, FINISHED, NEVER_OPENED, FAVORITES, LONG_BOOKS, SHORT_READS,
+
+    /** Books in no collection: the ones standing beside the home bookshelf. */
+    UNSORTED,
 }
 
 data class TagRef(val id: Long, val name: String)
@@ -137,6 +140,7 @@ object LibraryQuery {
             book.pageCount?.let { it >= LONG_BOOK_PAGES } ?: (book.fileSize >= LONG_BOOK_BYTES)
         SmartCollection.SHORT_READS ->
             book.pageCount?.let { it in 1 until SHORT_READ_PAGES } ?: (book.fileSize < LONG_BOOK_BYTES / 10)
+        SmartCollection.UNSORTED -> book.collectionIds.isEmpty()
     }
 
     fun sort(books: List<LibraryBook>, sort: BookSort, ascending: Boolean): List<LibraryBook> {
```

### 3. `app/src/main/java/app/folio/core/model/Bookshelf.kt`

**New, pure Kotlin.** `BookshelfBuilder.build()` turns library books, collections and collection links into the shelves (in collection order), a "Currently reading" shelf and the unsorted books. `BookLooks.of()` gives each book a stable look: height, thickness from page count, an occasional lean, and a spine decoration.

Full source:

```kotlin
package app.folio.core.model

import kotlin.math.absoluteValue
import kotlin.math.ln

/** A collection as the bookshelf needs it. */
data class ShelfCollection(val id: Long, val name: String)

/** A book's place in a collection. */
data class ShelfLink(val collectionId: Long, val bookId: Long, val position: Int)

/** One shelf: a collection's books in the collection's own order. */
data class Shelf(
    val collectionId: Long,
    val name: String,
    val books: List<LibraryBook>,
)

/**
 * The home bookshelf, built entirely from library data: the books being read, one shelf per
 * collection, and the books that are on no shelf.
 */
data class Bookshelf(
    val reading: List<LibraryBook> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val unsorted: List<LibraryBook> = emptyList(),
) {
    val isEmpty: Boolean get() = reading.isEmpty() && unsorted.isEmpty() && shelves.all { it.books.isEmpty() }
}

object BookshelfBuilder {

    const val READING_LIMIT = 20

    fun build(
        books: List<LibraryBook>,
        collections: List<ShelfCollection>,
        links: List<ShelfLink>,
        now: Long,
    ): Bookshelf {
        val byId = books.associateBy { it.id }
        val collectionIds = collections.mapTo(HashSet()) { it.id }
        val linksByCollection = links.filter { it.collectionId in collectionIds }.groupBy { it.collectionId }

        val shelves = collections.map { collection ->
            Shelf(
                collectionId = collection.id,
                name = collection.name,
                books = linksByCollection[collection.id].orEmpty()
                    .sortedBy { it.position }
                    .mapNotNull { byId[it.bookId] },
            )
        }

        val shelved = linksByCollection.values.flatten().mapTo(HashSet()) { it.bookId }
        val unsorted = books
            .filter { it.id !in shelved }
            .sortedByDescending { it.dateAdded }

        val reading = books
            .filter { !it.missing && LibraryQuery.matchesSmart(it, SmartCollection.CURRENTLY_READING, now) }
            .sortedByDescending { it.lastOpenedAt ?: 0L }
            .take(READING_LIMIT)

        return Bookshelf(reading = reading, shelves = shelves, unsorted = unsorted)
    }
}

/**
 * How a book stands on a shelf. Derived from the book itself so it never changes between visits:
 * thickness follows the page count, height and lean vary a little per book.
 */
data class BookLook(
    /** Share of the shelf's height, 0.8 to 1. */
    val heightFraction: Float,
    /** 0 for a slim book, 1 for a thick one. */
    val thickness: Float,
    /** A slight lean for a few spines; 0 for most books. */
    val tiltDegrees: Float,
    /** Which of the spine decorations to draw. */
    val band: Int,
)

object BookLooks {

    const val BANDS = 3
    private const val MIN_PAGES = 40.0
    private const val MAX_PAGES = 1200.0

    fun of(book: LibraryBook, leanAllowed: Boolean = true): BookLook {
        val hash = mix(book.id)
        val pages = book.pageCount?.takeIf { it > 0 }?.toDouble()
            // No page count (comics folders, text): estimate from the file size, about 60 KB a page.
            ?: (book.fileSize / 60_000.0).coerceAtLeast(MIN_PAGES)
        val thickness = ((ln(pages) - ln(MIN_PAGES)) / (ln(MAX_PAGES) - ln(MIN_PAGES)))
            .coerceIn(0.0, 1.0)
            .toFloat()
        val lean = leanAllowed && hash % 7 == 3L
        return BookLook(
            heightFraction = 0.8f + (hash % 21) / 100f,
            thickness = thickness,
            tiltDegrees = if (lean) (if ((hash / 7) % 2 == 0L) -4f else 4f) else 0f,
            band = ((hash / 21) % BANDS).toInt(),
        )
    }

    /** A stable, well-spread hash of a book id. */
    private fun mix(id: Long): Long {
        var x = id + 0x9E3779B97F4A7C15uL.toLong()
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
        x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
        return (x xor (x ushr 31)).absoluteValue.coerceAtLeast(0L)
    }
}
```

### 4. `app/src/main/java/app/folio/core/model/Music.kt`

**`MusicPlan`** and **`MusicInheritance.effective()`**: a book plays its own music; otherwise it plays the soundtrack of the first of its collections, in shelf order, that has one. A plan with no sources counts as no music.

Diff:

```diff
@@ -38,6 +38,25 @@ enum class BookMusicMode { LOOP, SHUFFLE, SELECTION }
 /** One attached source of a book's music: a whole collection or a single track. */
 data class MusicSourceRef(val collectionId: Long?, val trackId: Long?)
 
+/** Music attached to a book or a book collection, as the player needs it. */
+data class MusicPlan(
+    val mode: BookMusicMode,
+    val autoplay: Boolean,
+    val sources: List<MusicSourceRef>,
+    val selection: List<Long> = emptyList(),
+)
+
+/** Which music a book plays: its own, else a soundtrack it gets from one of its collections. */
+object MusicInheritance {
+
+    /**
+     * The book's own music wins. Otherwise the first of its collections, in shelf order, that has
+     * any music. A plan with no sources counts as no music.
+     */
+    fun effective(own: MusicPlan?, fromCollections: List<MusicPlan>): MusicPlan? =
+        own?.takeIf { it.sources.isNotEmpty() } ?: fromCollections.firstOrNull { it.sources.isNotEmpty() }
+}
+
 object MusicQueueBuilder {
 
     /** Tracks attached to a book, in source order, each once, skipping tracks that are unavailable. */
```

## Database (Room 3 → 4)

### 5. `app/src/main/java/app/folio/data/db/MusicEntities.kt`

New tables `collection_music` (one row per book collection: mode, autoplay) and `collection_music_sources` (whole playlists or single tracks, in order). Rows are removed automatically when the collection, playlist or track is deleted.

Diff:

```diff
@@ -113,6 +113,41 @@ data class BookMusicSourceEntity(
 @Serializable
 data class BookMusicSelectionEntity(val bookId: Long, val trackId: Long, val position: Int)
 
+/**
+ * A book collection's soundtrack. Books in the collection play it when they have no music of
+ * their own; the book's own music always wins.
+ */
+@Entity(
+    tableName = "collection_music",
+    foreignKeys = [ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE)],
+)
+@Serializable
+data class CollectionMusicEntity(
+    @PrimaryKey val collectionId: Long,
+    val mode: BookMusicMode = BookMusicMode.LOOP,
+    val autoplay: Boolean = true,
+    val updatedAt: Long,
+)
+
+/** One source of a collection's soundtrack: a whole playlist (music collection) or a single track. */
+@Entity(
+    tableName = "collection_music_sources",
+    foreignKeys = [
+        ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE),
+        ForeignKey(MusicCollectionEntity::class, ["id"], ["musicCollectionId"], onDelete = ForeignKey.CASCADE),
+        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
+    ],
+    indices = [Index("collectionId"), Index("musicCollectionId"), Index("trackId")],
+)
+@Serializable
+data class CollectionMusicSourceEntity(
+    @PrimaryKey(autoGenerate = true) val id: Long = 0,
+    val collectionId: Long,
+    val musicCollectionId: Long? = null,
+    val trackId: Long? = null,
+    val position: Int,
+)
+
 data class MusicCollectionWithCount(
     @Embedded val collection: MusicCollectionEntity,
     val trackCount: Int,
```

### 6. `app/src/main/java/app/folio/data/db/FolioDatabase.kt`

Registers the two entities. Version 3 → 4 through `AutoMigration(3, 4)`, which only adds tables, so existing libraries are untouched.

Diff:

```diff
@@ -33,10 +33,17 @@ import androidx.room.RoomDatabase
         BookMusicSelectionEntity::class,
         DrawnNoteEntity::class,
         InkStrokeEntity::class,
+        CollectionMusicEntity::class,
+        CollectionMusicSourceEntity::class,
     ],
-    version = 3,
+    version = 4,
     exportSchema = true,
-    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
+    autoMigrations = [
+        AutoMigration(from = 1, to = 2),
+        AutoMigration(from = 2, to = 3),
+        // Collection soundtracks: two new tables, nothing existing changes.
+        AutoMigration(from = 3, to = 4),
+    ],
 )
 abstract class FolioDatabase : RoomDatabase() {
     abstract fun books(): BookDao
```

### 7. `app/src/main/java/app/folio/data/db/MusicDao.kt`

Queries to read, save and clear a collection's soundtrack, *which books / collections have music* (for the ♪ marks), `collectionMusicForBook()` (a book's collections that have a soundtrack, in shelf order), and backup helpers.

Diff:

```diff
@@ -84,4 +84,43 @@ interface MusicDao {
     @Query("SELECT * FROM book_music_sources") suspend fun allSources(): List<BookMusicSourceEntity>
     @Query("SELECT * FROM book_music_selection") suspend fun allSelections(): List<BookMusicSelectionEntity>
     @Query("DELETE FROM book_music") suspend fun deleteAllBookMusic()
+
+    /** Books with music of their own, for the "has music" marks in pickers. */
+    @Query("SELECT DISTINCT bookId FROM book_music_sources") fun observeBooksWithMusic(): Flow<List<Long>>
+
+    // ---- Collection soundtracks ------------------------------------------------------------
+
+    @Upsert suspend fun upsertCollectionMusic(entity: CollectionMusicEntity)
+    @Query("SELECT * FROM collection_music WHERE collectionId = :collectionId")
+    suspend fun collectionMusic(collectionId: Long): CollectionMusicEntity?
+    @Query("SELECT * FROM collection_music WHERE collectionId = :collectionId")
+    fun observeCollectionMusic(collectionId: Long): Flow<CollectionMusicEntity?>
+    @Query("SELECT * FROM collection_music_sources WHERE collectionId = :collectionId ORDER BY position")
+    suspend fun collectionSources(collectionId: Long): List<CollectionMusicSourceEntity>
+    @Query("SELECT * FROM collection_music_sources WHERE collectionId = :collectionId ORDER BY position")
+    fun observeCollectionSources(collectionId: Long): Flow<List<CollectionMusicSourceEntity>>
+    @Query("DELETE FROM collection_music_sources WHERE collectionId = :collectionId")
+    suspend fun clearCollectionSources(collectionId: Long)
+    @Insert suspend fun insertCollectionSources(sources: List<CollectionMusicSourceEntity>)
+    @Query("DELETE FROM collection_music WHERE collectionId = :collectionId")
+    suspend fun deleteCollectionMusic(collectionId: Long)
+
+    /** Collections with a soundtrack, for the "has music" marks in pickers. */
+    @Query("SELECT DISTINCT collectionId FROM collection_music_sources")
+    fun observeCollectionsWithMusic(): Flow<List<Long>>
+
+    /** The collections a book is in that have a soundtrack, in shelf order. */
+    @Query(
+        """SELECT cm.* FROM collection_music cm
+           JOIN collection_books cb ON cb.collectionId = cm.collectionId
+           JOIN collections c ON c.id = cm.collectionId
+           WHERE cb.bookId = :bookId
+           ORDER BY c.sortOrder, c.createdAt""",
+    )
+    suspend fun collectionMusicForBook(bookId: Long): List<CollectionMusicEntity>
+
+    @Query("SELECT * FROM collection_music") suspend fun allCollectionMusic(): List<CollectionMusicEntity>
+    @Query("SELECT * FROM collection_music_sources") suspend fun allCollectionSources(): List<CollectionMusicSourceEntity>
+    @Query("DELETE FROM collection_music_sources") suspend fun deleteAllCollectionSources()
+    @Query("DELETE FROM collection_music") suspend fun deleteAllCollectionMusic()
 }
```

### 8. `app/src/androidTest/java/app/folio/MigrationTest.kt`

An instrumented test that a v3 library (a book, a collection and its link) survives the upgrade to v4, and the new tables start empty.

Diff:

```diff
@@ -75,8 +75,43 @@ class MigrationTest {
         }
     }
 
+    @Test
+    fun version3LibrarySurvivesCollectionMusicMigration() {
+        helper.createDatabase(DB_SHELF_MUSIC, 3).use { db ->
+            db.execSQL(
+                """INSERT INTO books (id, uri, fileName, fileSize, fileHash, mimeType, format, storage,
+                   pageCount, coverHidden, displayTitle, sortTitle, status, statusManual, favorite, progress,
+                   dateAdded, missing, passwordProtected, indexState, customOrder)
+                   VALUES (1, 'content://x/book.pdf', 'book.pdf', 10, 'h', 'application/pdf', 'PDF', 'LINKED',
+                   10, 0, 'Kept Book', 'kept book', 'READING', 0, 0, 0.5,
+                   0, 0, 0, 'PENDING', 0)""",
+            )
+            db.execSQL("INSERT INTO collections (id, name, sortOrder, createdAt) VALUES (1, 'Science', 0, 0)")
+            db.execSQL("INSERT INTO collection_books (collectionId, bookId, position, addedAt) VALUES (1, 1, 0, 0)")
+        }
+        helper.runMigrationsAndValidate(DB_SHELF_MUSIC, 4, true).use { db ->
+            db.query("SELECT name FROM collections WHERE id = 1").use { cursor ->
+                cursor.moveToFirst()
+                assertEquals("Science", cursor.getString(0))
+            }
+            db.query("SELECT COUNT(*) FROM collection_books").use { cursor ->
+                cursor.moveToFirst()
+                assertEquals(1, cursor.getInt(0))
+            }
+            db.query("SELECT COUNT(*) FROM collection_music").use { cursor ->
+                cursor.moveToFirst()
+                assertEquals(0, cursor.getInt(0))
+            }
+            db.query("SELECT COUNT(*) FROM collection_music_sources").use { cursor ->
+                cursor.moveToFirst()
+                assertEquals(0, cursor.getInt(0))
+            }
+        }
+    }
+
     private companion object {
         const val DB = "migration-test"
         const val DB_INK = "migration-test-ink"
+        const val DB_SHELF_MUSIC = "migration-test-shelf-music"
     }
 }
```

## Repositories, playback and backup

### 9. `app/src/main/java/app/folio/data/repo/MusicRepository.kt`

Adds:
- `CollectionMusicSettings`, plus `observeCollectionMusic`, `saveCollectionMusic` and `clearCollectionMusic`.
- `booksWithMusic` and `collectionsWithMusic`.
- `createPlaylist(name, trackIds)`.
- `effectiveMusic(bookId)`: the book's own music, or the soundtrack inherited from a collection.

`resolveBookQueue` now builds the queue from `effectiveMusic`, so a book in a collection with a soundtrack gets music.

Diff:

```diff
@@ -7,6 +7,8 @@ import android.net.Uri
 import androidx.room.withTransaction
 import app.folio.core.model.BookMusicMode
 import app.folio.core.model.MetadataResolver
+import app.folio.core.model.MusicInheritance
+import app.folio.core.model.MusicPlan
 import app.folio.core.model.MusicQueueBuilder
 import app.folio.core.model.MusicSourceRef
 import app.folio.core.model.TagNormalizer
@@ -15,6 +17,8 @@ import app.folio.core.model.TrackMetadataResolver
 import app.folio.data.db.BookMusicEntity
 import app.folio.data.db.BookMusicSelectionEntity
 import app.folio.data.db.BookMusicSourceEntity
+import app.folio.data.db.CollectionMusicEntity
+import app.folio.data.db.CollectionMusicSourceEntity
 import app.folio.data.db.FolioDatabase
 import app.folio.data.db.MusicCollectionEntity
 import app.folio.data.db.MusicCollectionTrackEntity
@@ -49,6 +53,14 @@ data class NewTrack(
     val cover: Bitmap?,
 )
 
+/** A book collection's soundtrack: whole playlists and single tracks, with how to play them. */
+data class CollectionMusicSettings(
+    val mode: BookMusicMode,
+    val autoplay: Boolean,
+    val playlistIds: List<Long>,
+    val trackIds: List<Long>,
+)
+
 data class BookMusicSettings(
     val mode: BookMusicMode,
     val autoplay: Boolean,
@@ -88,6 +100,26 @@ class MusicRepository(
         music?.let { BookMusicSettings(it.mode, it.autoplay, sources, selection.map { row -> row.trackId }) }
     }
 
+    /** Ids of books that have music of their own. */
+    val booksWithMusic: Flow<List<Long>> = dao.observeBooksWithMusic()
+
+    /** Ids of book collections that have a soundtrack. */
+    val collectionsWithMusic: Flow<List<Long>> = dao.observeCollectionsWithMusic()
+
+    fun observeCollectionMusic(collectionId: Long): Flow<CollectionMusicSettings?> = combine(
+        dao.observeCollectionMusic(collectionId),
+        dao.observeCollectionSources(collectionId),
+    ) { music, sources ->
+        music?.let {
+            CollectionMusicSettings(
+                mode = it.mode,
+                autoplay = it.autoplay,
+                playlistIds = sources.mapNotNull { source -> source.musicCollectionId },
+                trackIds = sources.mapNotNull { source -> source.trackId },
+            )
+        }
+    }
+
     suspend fun importUri(uri: String): MusicImportResult = withContext(Dispatchers.IO) {
         val info = files.info(uri) ?: return@withContext MusicImportResult.Unreadable
         if (!isAudio(info.name, info.mimeType)) return@withContext MusicImportResult.Unreadable
@@ -194,6 +226,13 @@ class MusicRepository(
 
     suspend fun deleteCollection(id: Long) = dao.deleteCollection(id)
 
+    /** Creates a playlist (music collection) holding [trackIds] in that order, and returns its id. */
+    suspend fun createPlaylist(name: String, trackIds: List<Long>): Long = db.withTransaction {
+        val id = createCollection(name)
+        addToCollection(id, trackIds)
+        id
+    }
+
     suspend fun addToCollection(collectionId: Long, trackIds: List<Long>) = db.withTransaction {
         var position = dao.lastPosition(collectionId)
         dao.addToCollection(trackIds.map { MusicCollectionTrackEntity(collectionId, it, ++position) })
@@ -219,6 +258,45 @@ class MusicRepository(
         dao.deleteBookMusic(bookId)
     }
 
+    suspend fun saveCollectionMusic(collectionId: Long, settings: CollectionMusicSettings) = db.withTransaction {
+        dao.upsertCollectionMusic(
+            CollectionMusicEntity(collectionId, settings.mode, settings.autoplay, System.currentTimeMillis()),
+        )
+        dao.clearCollectionSources(collectionId)
+        val sources = settings.playlistIds.map { CollectionMusicSourceEntity(collectionId = collectionId, musicCollectionId = it, position = 0) } +
+            settings.trackIds.map { CollectionMusicSourceEntity(collectionId = collectionId, trackId = it, position = 0) }
+        dao.insertCollectionSources(sources.mapIndexed { index, source -> source.copy(position = index) })
+    }
+
+    suspend fun clearCollectionMusic(collectionId: Long) = db.withTransaction {
+        dao.clearCollectionSources(collectionId)
+        dao.deleteCollectionMusic(collectionId)
+    }
+
+    /**
+     * What a book plays when it opens: its own music, or else the soundtrack of the first of its
+     * collections (in shelf order) that has one. Null when neither exists.
+     */
+    suspend fun effectiveMusic(bookId: Long): MusicPlan? {
+        val own = bookMusicSettings(bookId)?.let { settings ->
+            MusicPlan(
+                mode = settings.mode,
+                autoplay = settings.autoplay,
+                sources = settings.sources.map { MusicSourceRef(it.collectionId, it.trackId) },
+                selection = settings.selection,
+            )
+        }
+        if (own != null && own.sources.isNotEmpty()) return own
+        val inherited = dao.collectionMusicForBook(bookId).map { music ->
+            MusicPlan(
+                mode = music.mode,
+                autoplay = music.autoplay,
+                sources = dao.collectionSources(music.collectionId).map { MusicSourceRef(it.musicCollectionId, it.trackId) },
+            )
+        }
+        return MusicInheritance.effective(own, inherited)
+    }
+
     suspend fun hasMusic(bookId: Long): Boolean = dao.bookMusic(bookId) != null && dao.sources(bookId).isNotEmpty()
 
     suspend fun bookMusicSettings(bookId: Long): BookMusicSettings? {
@@ -226,9 +304,12 @@ class MusicRepository(
         return BookMusicSettings(music.mode, music.autoplay, dao.sources(bookId), dao.selection(bookId).map { it.trackId })
     }
 
-    /** The tracks to play for a book now; files that disappeared are marked missing and skipped. */
+    /**
+     * The tracks to play for a book now, from its own music or its collection's soundtrack; files
+     * that disappeared are marked missing and skipped.
+     */
     suspend fun resolveBookQueue(bookId: Long, seed: Long): List<TrackEntity> = withContext(Dispatchers.IO) {
-        val settings = bookMusicSettings(bookId) ?: return@withContext emptyList()
+        val plan = effectiveMusic(bookId) ?: return@withContext emptyList()
         val collectionTracks = dao.allCollectionLinks().groupBy({ it.collectionId }, { it.trackId })
         val all = dao.allTracks().associateBy { it.id }
         val available = all.values.filter { track ->
@@ -236,12 +317,8 @@ class MusicRepository(
             if (present == track.missing) dao.setMissing(track.id, !present)
             present
         }.map { it.id }.toSet()
-        val attached = MusicQueueBuilder.attachedTracks(
-            settings.sources.map { MusicSourceRef(it.collectionId, it.trackId) },
-            collectionTracks,
-            available,
-        )
-        MusicQueueBuilder.forBook(settings.mode, attached, settings.selection, seed).mapNotNull { all[it] }
+        val attached = MusicQueueBuilder.attachedTracks(plan.sources, collectionTracks, available)
+        MusicQueueBuilder.forBook(plan.mode, attached, plan.selection, seed).mapNotNull { all[it] }
     }
 
     suspend fun markMissing(trackId: Long) = dao.setMissing(trackId, true)
```

### 10. `app/src/main/java/app/folio/music/BookMusicCoordinator.kt`

When a book opens, autoplay decides from `effectiveMusic()` instead of the book's own settings only.

Diff:

```diff
@@ -64,7 +64,8 @@ class BookMusicCoordinator(
 
     fun onBookOpened(bookId: Long, family: ReaderFamily) {
         scope.launch {
-            val saved = if (MusicSwitchPolicy.supports(family)) music.bookMusicSettings(bookId) else null
+            // The book's own music, or the soundtrack it gets from one of its collections.
+            val saved = if (MusicSwitchPolicy.supports(family)) music.effectiveMusic(bookId) else null
             val plan = BookMusicPlan(
                 bookId = bookId,
                 family = family,
```

### 11. `app/src/main/java/app/folio/data/backup/BackupRepository.kt`

Backups include `collectionMusic` and `collectionMusicSources`. The restore clears them and reinserts them after collections, playlists and tracks. Older backups restore because the new fields default to empty.

Diff:

```diff
@@ -3,6 +3,8 @@ package app.folio.data.backup
 import app.folio.data.db.BookMusicEntity
 import app.folio.data.db.BookMusicSelectionEntity
 import app.folio.data.db.BookMusicSourceEntity
+import app.folio.data.db.CollectionMusicEntity
+import app.folio.data.db.CollectionMusicSourceEntity
 import app.folio.data.db.MusicCollectionEntity
 import app.folio.data.db.MusicCollectionTrackEntity
 import app.folio.data.db.MusicTagEntity
@@ -65,6 +67,8 @@ data class BackupDocument(
     val bookMusic: List<BookMusicEntity> = emptyList(),
     val bookMusicSources: List<BookMusicSourceEntity> = emptyList(),
     val bookMusicSelections: List<BookMusicSelectionEntity> = emptyList(),
+    val collectionMusic: List<CollectionMusicEntity> = emptyList(),
+    val collectionMusicSources: List<CollectionMusicSourceEntity> = emptyList(),
     val drawnNotes: List<DrawnNoteEntity> = emptyList(),
     val inkStrokes: List<InkStrokeEntity> = emptyList(),
     val settings: String? = null,
@@ -124,6 +128,8 @@ class BackupRepository(
                 bookMusic = db.music().allBookMusic(),
                 bookMusicSources = db.music().allSources(),
                 bookMusicSelections = db.music().allSelections(),
+                collectionMusic = db.music().allCollectionMusic(),
+                collectionMusicSources = db.music().allCollectionSources(),
                 drawnNotes = db.ink().allDrawnNotes(),
                 inkStrokes = db.ink().allStrokes(),
                 settings = settings.exportJson(),
@@ -223,6 +229,8 @@ class BackupRepository(
                 db.collections().deleteAll()
                 db.tags().deleteAll()
                 db.music().deleteAllBookMusic()
+                db.music().deleteAllCollectionSources()
+                db.music().deleteAllCollectionMusic()
                 db.music().deleteAllCollections()
                 db.music().deleteAllTags()
                 db.music().deleteAllTracks()
@@ -278,6 +286,9 @@ class BackupRepository(
                     backup.bookMusic.forEach { dao.upsertBookMusic(it) }
                     dao.insertSources(backup.bookMusicSources)
                     dao.insertSelection(backup.bookMusicSelections)
+                    // After books' collections and the playlists they point at are back.
+                    backup.collectionMusic.forEach { dao.upsertCollectionMusic(it) }
+                    dao.insertCollectionSources(backup.collectionMusicSources)
                 }
             }
 
```

## State and navigation

### 12. `app/src/main/java/app/folio/AppContainer.kt`

`libraryRequest`: a one-shot hand-off that opens the library filtered to a smart collection.

Diff:

```diff
@@ -79,6 +79,12 @@ class AppContainer(context: Context) {
         app.folio.music.BookMusicCoordinator(music, musicPlayer, pomodoro, settings, appScope)
     }
 
+    /**
+     * A smart collection the library should show the next time it appears, set by shortcuts that
+     * lead into the library (the home bookshelf, the Collections screen). The library clears it.
+     */
+    val libraryRequest = kotlinx.coroutines.flow.MutableStateFlow<app.folio.core.model.SmartCollection?>(null)
+
     val backup: app.folio.data.backup.BackupRepository by lazy {
         app.folio.data.backup.BackupRepository(database, settings, files, cache, library)
     }
```

### 13. `app/src/main/java/app/folio/ui/screens/library/LibraryViewModel.kt`

Takes the `libraryRequest`, applies it as the library filter, then clears it.

Diff:

```diff
@@ -87,6 +87,14 @@ class LibraryViewModel(private val container: AppContainer) : ViewModel() {
     val importProgress: StateFlow<ImportProgress> = container.imports.progress
 
     init {
+        viewModelScope.launch {
+            container.libraryRequest.collect { smart ->
+                if (smart != null) {
+                    _filter.value = LibraryFilter(smart = smart)
+                    container.libraryRequest.value = null
+                }
+            }
+        }
         viewModelScope.launch {
             container.imports.events.collect { event ->
                 if (event is ImportEvent.Duplicate) _duplicate.value = event.pending
```

### 14. `app/src/main/java/app/folio/ui/screens/home/HomeViewModel.kt`

A `bookshelf` StateFlow combining books, collections and links. It starts as `null` so the screen doesn't flash an empty library while loading.

Diff:

```diff
@@ -3,9 +3,13 @@ package app.folio.ui.screens.home
 import androidx.lifecycle.ViewModel
 import androidx.lifecycle.viewModelScope
 import app.folio.AppContainer
+import app.folio.core.model.Bookshelf
+import app.folio.core.model.BookshelfBuilder
 import app.folio.core.model.LibraryBook
 import app.folio.core.model.LibraryQuery
 import app.folio.core.model.ReadingStatus
+import app.folio.core.model.ShelfCollection
+import app.folio.core.model.ShelfLink
 import app.folio.core.model.StatsCalculator
 import app.folio.data.db.CollectionWithCount
 import app.folio.data.db.GoalType
@@ -63,6 +67,23 @@ class HomeViewModel(private val container: AppContainer) : ViewModel() {
     val collections: StateFlow<List<CollectionWithCount>> = container.organization.collections
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
 
+    /** Null until the library has loaded, so the home screen never flashes an empty shelf. */
+    val bookshelf: StateFlow<Bookshelf?> =
+        combine(
+            container.library.libraryBooks,
+            container.organization.collections,
+            container.organization.collectionLinks,
+        ) { library, collections, links ->
+            BookshelfBuilder.build(
+                books = library,
+                collections = collections.map { ShelfCollection(it.collection.id, it.collection.name) },
+                links = links.map { ShelfLink(it.collectionId, it.bookId, it.position) },
+                now = System.currentTimeMillis(),
+            )
+        }
+            .flowOn(Dispatchers.Default)
+            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
+
     val queue: StateFlow<List<LibraryBook>> =
         combine(container.organization.queue, books) { queue, library ->
             val byId = library.associateBy { it.id }
```

### 15. `app/src/main/java/app/folio/ui/FolioRoot.kt`

Connects the home callbacks to the existing routes (search, settings, music, filtered library). Also fixes the Collections screen, whose smart-collection chips used to open the library unfiltered.

Diff:

```diff
@@ -166,6 +166,7 @@ private fun FolioNavHost(
     wide: Boolean,
     modifier: Modifier = Modifier,
 ) {
+    val container = LocalContainer.current
     NavHost(navController, startDestination = Routes.HOME, modifier = modifier) {
         composable(Routes.HOME) {
             HomeScreen(
@@ -177,6 +178,13 @@ private fun FolioNavHost(
                 onStats = { navController.navigateTop(Routes.STATS) },
                 onQueue = { navController.navigate(Routes.QUEUE) },
                 onPomodoro = { navController.navigate(Routes.POMODORO) },
+                onSearch = { navController.navigate(Routes.SEARCH) },
+                onSettings = { navController.navigateTop(Routes.SETTINGS) },
+                onMusic = { navController.navigate(Routes.MUSIC) },
+                onLibrarySmart = { smart ->
+                    container.libraryRequest.value = smart
+                    navController.navigateTop(Routes.LIBRARY)
+                },
             )
         }
 
@@ -340,7 +348,10 @@ private fun FolioNavHost(
             CollectionsScreen(
                 onBack = { navController.popBackStack() },
                 onOpenCollection = { navController.navigate(Routes.collection(it)) },
-                onOpenSmart = { navController.navigateTop(Routes.LIBRARY) },
+                onOpenSmart = { smart ->
+                    container.libraryRequest.value = smart
+                    navController.navigateTop(Routes.LIBRARY)
+                },
                 onCategories = { navController.navigate(Routes.CATEGORIES) },
                 onTags = { navController.navigate(Routes.TAGS) },
                 onQueue = { navController.navigate(Routes.QUEUE) },
```

## Home screen UI

### 16. `app/src/main/java/app/folio/ui/screens/home/HomeScreen.kt`

`HomeScreen` shows either `BookshelfHome` or `ClassicHome`, which is the original home moved unchanged. `BookshelfHome` lays out the header, the music card, then the bookcase or the empty-library message.

Diff:

```diff
@@ -8,6 +8,8 @@ import androidx.compose.foundation.background
 import androidx.compose.foundation.clickable
 import androidx.compose.foundation.layout.Arrangement
 import androidx.compose.foundation.layout.Box
+import androidx.compose.foundation.layout.BoxWithConstraints
+import androidx.compose.foundation.layout.fillMaxSize
 import androidx.compose.foundation.layout.Column
 import androidx.compose.foundation.layout.PaddingValues
 import androidx.compose.foundation.layout.Row
@@ -20,6 +22,8 @@ import androidx.compose.foundation.layout.size
 import androidx.compose.foundation.layout.width
 import androidx.compose.foundation.layout.statusBarsPadding
 import androidx.compose.foundation.lazy.LazyColumn
+import androidx.compose.foundation.rememberScrollState
+import androidx.compose.foundation.verticalScroll
 import androidx.compose.foundation.lazy.LazyRow
 import androidx.compose.foundation.lazy.items
 import androidx.compose.foundation.shape.CircleShape
@@ -38,6 +42,7 @@ import androidx.compose.material3.Text
 import androidx.compose.material3.TextButton
 import androidx.compose.runtime.Composable
 import androidx.compose.runtime.getValue
+import androidx.compose.runtime.remember
 import androidx.compose.ui.Alignment
 import androidx.compose.ui.Modifier
 import androidx.compose.ui.draw.clip
@@ -52,7 +57,9 @@ import androidx.compose.ui.unit.dp
 import androidx.lifecycle.compose.collectAsStateWithLifecycle
 import app.folio.R
 import app.folio.core.model.LibraryBook
+import app.folio.core.model.SmartCollection
 import app.folio.data.settings.HomeSection
+import app.folio.data.settings.HomeStyle
 import app.folio.ui.components.BookCover
 import app.folio.ui.components.EmptyState
 import app.folio.ui.components.ProgressBar
@@ -72,7 +79,124 @@ fun HomeScreen(
     onStats: () -> Unit,
     onQueue: () -> Unit,
     onPomodoro: () -> Unit,
+    onSearch: () -> Unit,
+    onSettings: () -> Unit,
+    onMusic: () -> Unit,
+    onLibrarySmart: (SmartCollection) -> Unit,
     viewModel: HomeViewModel = folioViewModel { HomeViewModel(it) },
+) {
+    val settings by viewModel.settings.collectAsStateWithLifecycle()
+    if (settings.home.style == HomeStyle.BOOKSHELF) {
+        BookshelfHome(
+            viewModel = viewModel,
+            onOpenBook = onOpenBook,
+            onBookDetails = onBookDetails,
+            onCollections = onCollections,
+            onCollection = onCollection,
+            onPomodoro = onPomodoro,
+            onSearch = onSearch,
+            onSettings = onSettings,
+            onMusic = onMusic,
+            onLibrarySmart = onLibrarySmart,
+        )
+    } else {
+        ClassicHome(
+            onOpenBook = onOpenBook,
+            onBookDetails = onBookDetails,
+            onSeeLibrary = onSeeLibrary,
+            onCollections = onCollections,
+            onCollection = onCollection,
+            onStats = onStats,
+            onQueue = onQueue,
+            onPomodoro = onPomodoro,
+            viewModel = viewModel,
+        )
+    }
+}
+
+/**
+ * The bookshelf home: header with the clock, the music player, then the library itself as shelves.
+ * Everything on it comes from the library, collections and player the rest of the app uses.
+ */
+@Composable
+private fun BookshelfHome(
+    viewModel: HomeViewModel,
+    onOpenBook: (Long) -> Unit,
+    onBookDetails: (Long) -> Unit,
+    onCollections: () -> Unit,
+    onCollection: (Long) -> Unit,
+    onPomodoro: () -> Unit,
+    onSearch: () -> Unit,
+    onSettings: () -> Unit,
+    onMusic: () -> Unit,
+    onLibrarySmart: (SmartCollection) -> Unit,
+) {
+    val bookshelf by viewModel.bookshelf.collectAsStateWithLifecycle()
+    val pomodoro by viewModel.pomodoro.collectAsStateWithLifecycle()
+    val remaining by viewModel.pomodoroRemaining.collectAsStateWithLifecycle()
+    val spacing = LocalSpacing.current
+    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
+        if (uris.isNotEmpty()) viewModel.import(uris.map { it.toString() })
+    }
+    val padding = if (spacing.screenPadding > 16.dp) 16.dp else spacing.screenPadding
+
+    BoxWithConstraints(Modifier.fillMaxSize()) {
+        val metrics = remember(maxWidth, padding) { shelfMetrics(maxWidth - padding * 2) }
+        Column(
+            Modifier
+                .fillMaxSize()
+                .verticalScroll(rememberScrollState())
+                .statusBarsPadding()
+                .padding(horizontal = padding)
+                .padding(top = 8.dp, bottom = 28.dp),
+            verticalArrangement = Arrangement.spacedBy(16.dp),
+        ) {
+            HomeHeader(
+                pomodoro = pomodoro,
+                remainingMs = remaining,
+                onClock = onPomodoro,
+                onSearch = onSearch,
+                onSettings = onSettings,
+                onLibraryHub = onCollections,
+            )
+            HomeMusicCard(onOpenMusicLibrary = onMusic)
+
+            val shelf = bookshelf
+            when {
+                // Still loading: leave the space empty rather than flash an empty-library message.
+                shelf == null -> Unit
+                shelf.isEmpty -> EmptyState(
+                    title = stringResource(R.string.empty_library_title),
+                    message = stringResource(R.string.empty_library_message),
+                    actionLabel = stringResource(R.string.action_import),
+                    onAction = { openFiles.launch(arrayOf("*/*")) },
+                )
+                else -> Bookcase(
+                    shelf = shelf,
+                    metrics = metrics,
+                    onOpenBook = onOpenBook,
+                    onBookDetails = onBookDetails,
+                    onOpenCollection = onCollection,
+                    onOpenSmart = onLibrarySmart,
+                    onCreateShelf = onCollections,
+                )
+            }
+        }
+    }
+}
+
+/** The original home: configurable sections (continue reading, goal, streak, activity, …). */
+@Composable
+private fun ClassicHome(
+    onOpenBook: (Long) -> Unit,
+    onBookDetails: (Long) -> Unit,
+    onSeeLibrary: () -> Unit,
+    onCollections: () -> Unit,
+    onCollection: (Long) -> Unit,
+    onStats: () -> Unit,
+    onQueue: () -> Unit,
+    onPomodoro: () -> Unit,
+    viewModel: HomeViewModel,
 ) {
     val settings by viewModel.settings.collectAsStateWithLifecycle()
     val continueReading by viewModel.continueReading.collectAsStateWithLifecycle()
```

### 17. `app/src/main/java/app/folio/ui/screens/home/HomeHeader.kt`

**New.** The Folio mark, a live clock (updates on the minute, follows 12/24 h) with a Pomodoro status chip, and the Search, Settings and Collections icons. Tapping the clock opens the Pomodoro screen.

Full source:

```kotlin
package app.folio.ui.screens.home

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.data.db.PomodoroPhase
import app.folio.pomodoro.PomodoroState
import app.folio.ui.util.Format
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The bookshelf home's header: the Folio mark, a live clock that opens the focus timer, and
 * shortcuts to search, settings and the collections hub. Narrow phones put the clock on its own
 * line so nothing has to shrink.
 */
@Composable
fun HomeHeader(
    pomodoro: PomodoroState,
    remainingMs: Long,
    onClock: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onLibraryHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val oneRow = maxWidth >= 560.dp
        if (oneRow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FolioMark(Modifier.weight(1f))
                HeaderClock(pomodoro, remainingMs, onClock)
                HeaderActions(onSearch, onSettings, onLibraryHub, Modifier.weight(1f))
            }
        } else {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FolioMark(Modifier.weight(1f))
                    HeaderActions(onSearch, onSettings, onLibraryHub)
                }
                HeaderClock(pomodoro, remainingMs, onClock)
            }
        }
    }
}

@Composable
private fun FolioMark(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Two leaning volumes: a small, quiet mark rather than a logo that competes with the books.
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(w * 0.06f)
            drawRoundRect(
                color = muted.copy(alpha = 0.45f),
                topLeft = Offset(w * 0.38f, h * 0.12f),
                size = Size(w * 0.22f, h * 0.76f),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(w * 0.08f, h * 0.04f),
                size = Size(w * 0.24f, h * 0.84f),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(w * 0.08f, h * 0.7f),
                size = Size(w * 0.84f, h * 0.18f),
                cornerRadius = corner,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HeaderActions(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onLibraryHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    Row(modifier, horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search), tint = tint)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.nav_settings), tint = tint)
        }
        IconButton(onClick = onLibraryHub) {
            Icon(
                Icons.Rounded.CollectionsBookmark,
                contentDescription = stringResource(R.string.home_your_library),
                tint = tint,
            )
        }
    }
}

/** The current time, updated on the minute. Tapping it opens the focus timer. */
@Composable
private fun HeaderClock(
    pomodoro: PomodoroState,
    remainingMs: Long,
    onOpenTimer: () -> Unit,
) {
    val context = LocalContext.current
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val use24h = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    val time = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm", locale))
    val amPm = if (use24h) null else now.format(DateTimeFormatter.ofPattern("a", locale))

    val status = when {
        !pomodoro.active -> stringResource(R.string.home_clock_timer)
        !pomodoro.running -> stringResource(R.string.home_clock_paused, Format.timer(remainingMs))
        pomodoro.phase == PomodoroPhase.FOCUS -> stringResource(R.string.home_clock_focus, Format.timer(remainingMs))
        else -> stringResource(R.string.home_clock_break, Format.timer(remainingMs))
    }
    val spoken = stringResource(R.string.home_clock_description, listOfNotNull(time, amPm).joinToString(" "), status)

    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenTimer)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = null) { onOpenTimer(); true }
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (amPm != null) {
            Text(
                text = amPm,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = CircleShape,
            color = if (pomodoro.active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = if (pomodoro.active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}
```

### 18. `app/src/main/java/app/folio/ui/screens/home/Bookshelf.kt`

**New.** The bookcase, top to bottom: **Currently reading**, one shelf per **collection**, and last the **Other books** shelf (books in no collection).
- Also here: responsive `ShelfMetrics`, a wood `ShelfPalette` tinted per theme, `ShelfRow` (a back panel with a `LazyRow` of books), the engraved `Plank` label, and `Spine` and `FaceOutCover` drawing.
- Tap opens the reader, long-press opens details, and a pressed book lifts slightly.

Full source:

```kotlin
package app.folio.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.core.model.BookLook
import app.folio.core.model.BookLooks
import app.folio.core.model.Bookshelf
import app.folio.core.model.LibraryBook
import app.folio.core.model.SmartCollection
import app.folio.ui.components.BookCover
import app.folio.ui.components.coverPalette
import app.folio.ui.theme.LocalFolioTheme
import app.folio.ui.theme.LocalReduceMotion
import app.folio.ui.util.Format
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.io.File

/** Sizes for one screen width. Books keep a usable size; long shelves scroll instead of shrinking. */
@Immutable
internal data class ShelfMetrics(
    val bookHeight: Dp,
    val headroom: Dp,
    val spineMin: Dp,
    val spineMax: Dp,
    val plankHeight: Dp,
)

private const val COVER_ASPECT = 0.68f

internal fun shelfMetrics(contentWidth: Dp): ShelfMetrics = when {
    contentWidth < 320.dp -> ShelfMetrics(
        bookHeight = 108.dp, headroom = 14.dp, spineMin = 20.dp, spineMax = 32.dp, plankHeight = 40.dp,
    )
    contentWidth < 560.dp -> ShelfMetrics(
        bookHeight = 128.dp, headroom = 16.dp, spineMin = 22.dp, spineMax = 38.dp, plankHeight = 40.dp,
    )
    else -> ShelfMetrics(
        bookHeight = 164.dp, headroom = 18.dp, spineMin = 26.dp, spineMax = 46.dp, plankHeight = 44.dp,
    )
}

/** Wood tones, nudged toward the theme's accent so each of the app's themes keeps its character. */
@Immutable
internal data class ShelfPalette(
    val frameTop: Color,
    val frameBottom: Color,
    val backTop: Color,
    val backBottom: Color,
    val plankLip: Color,
    val plankTop: Color,
    val plankFront: Color,
    val plankBottom: Color,
    val label: Color,
    val engrave: Color,
    val hint: Color,
)

@Composable
internal fun rememberShelfPalette(): ShelfPalette {
    val dark = LocalFolioTheme.current.isDark
    val surface = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary
    return remember(dark, surface, accent) {
        fun wood(argb: Long) = lerp(Color(argb), accent, 0.08f)
        if (dark) {
            ShelfPalette(
                frameTop = wood(0xFF4A3628),
                frameBottom = wood(0xFF33251C),
                backTop = lerp(Color(0xFF1C1612), surface, 0.35f),
                backBottom = lerp(Color(0xFF2A2019), surface, 0.3f),
                plankLip = wood(0xFF86664F),
                plankTop = wood(0xFF6B503E),
                plankFront = wood(0xFF533D2F),
                plankBottom = wood(0xFF3E2D22),
                label = Color(0xFFF2E4CF),
                engrave = Color.Black.copy(alpha = 0.5f),
                hint = Color(0xFFCDBBA5),
            )
        } else {
            ShelfPalette(
                frameTop = wood(0xFFC29C73),
                frameBottom = wood(0xFFA27B53),
                backTop = lerp(Color(0xFFE7D8C2), surface, 0.3f),
                backBottom = lerp(Color(0xFFD8C3A4), surface, 0.25f),
                plankLip = wood(0xFFEBD5B4),
                plankTop = wood(0xFFDDBF97),
                plankFront = wood(0xFFC9A577),
                plankBottom = wood(0xFFAB865B),
                label = Color(0xFF3A2716),
                engrave = Color.White.copy(alpha = 0.5f),
                hint = Color(0xFF6B5641),
            )
        }
    }
}

/**
 * The bookcase, top to bottom: the books being read, one shelf per collection, and last a shelf
 * of the books that are in no collection.
 */
@Composable
internal fun Bookcase(
    shelf: Bookshelf,
    metrics: ShelfMetrics,
    onOpenBook: (Long) -> Unit,
    onBookDetails: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenSmart: (SmartCollection) -> Unit,
    onCreateShelf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberShelfPalette()
    // Mostly spines, with the first book and the odd covered one turned face out.
    val collectionFaceOut: (Int, LibraryBook) -> Boolean =
        { index, book -> index == 0 || (index % 6 == 4 && book.coverPath != null) }

    Box(modifier.fillMaxWidth()) {
        BookcaseFrame(palette) {
            if (shelf.reading.isNotEmpty()) {
                ShelfRow(
                    title = stringResource(R.string.smart_currently_reading),
                    books = shelf.reading,
                    metrics = metrics,
                    palette = palette,
                    // What you're reading faces out: it is what you most likely want next.
                    faceOut = { index, _ -> index < 3 },
                    onOpenShelf = { onOpenSmart(SmartCollection.CURRENTLY_READING) },
                    onOpenBook = onOpenBook,
                    onBookDetails = onBookDetails,
                )
            }
            shelf.shelves.forEach { entry ->
                key(entry.collectionId) {
                    ShelfRow(
                        title = entry.name,
                        books = entry.books,
                        metrics = metrics,
                        palette = palette,
                        faceOut = collectionFaceOut,
                        onOpenShelf = { onOpenCollection(entry.collectionId) },
                        onOpenBook = onOpenBook,
                        onBookDetails = onBookDetails,
                        emptyHint = stringResource(R.string.home_shelf_empty),
                    )
                }
            }
            if (shelf.shelves.isEmpty()) {
                ShelfRow(
                    title = stringResource(R.string.home_first_shelf),
                    books = emptyList(),
                    metrics = metrics,
                    palette = palette,
                    faceOut = { _, _ -> false },
                    onOpenShelf = onCreateShelf,
                    onOpenBook = onOpenBook,
                    onBookDetails = onBookDetails,
                    emptyHint = stringResource(R.string.home_first_shelf_hint),
                )
            }
            // Always the bottom shelf: only the books that belong to no collection.
            if (shelf.unsorted.isNotEmpty()) {
                ShelfRow(
                    title = stringResource(R.string.home_other_books),
                    books = shelf.unsorted,
                    metrics = metrics,
                    palette = palette,
                    faceOut = collectionFaceOut,
                    onOpenShelf = { onOpenSmart(SmartCollection.UNSORTED) },
                    onOpenBook = onOpenBook,
                    onBookDetails = onBookDetails,
                )
            }
        }
    }
}

@Composable
private fun BookcaseFrame(palette: ShelfPalette, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .background(Brush.verticalGradient(listOf(palette.frameTop, palette.frameBottom)))
            .padding(start = 7.dp, end = 7.dp, top = 8.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp)),
        content = content,
    )
}

@Composable
private fun ShelfRow(
    title: String,
    books: List<LibraryBook>,
    metrics: ShelfMetrics,
    palette: ShelfPalette,
    faceOut: (Int, LibraryBook) -> Boolean,
    onOpenShelf: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onBookDetails: (Long) -> Unit,
    emptyHint: String? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(metrics.bookHeight + metrics.headroom)
                .background(Brush.verticalGradient(listOf(palette.backTop, palette.backBottom))),
        ) {
            if (books.isEmpty()) {
                Text(
                    text = emptyHint.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.hint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                        ShelfBook(
                            book = book,
                            faceOut = faceOut(index, book),
                            metrics = metrics,
                            onOpen = onOpenBook,
                            onDetails = onBookDetails,
                        )
                    }
                }
            }
            // The shelf above casts a soft shadow onto the back of this one.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent))),
            )
        }
        Plank(title = title, palette = palette, metrics = metrics, onClick = onOpenShelf)
    }
}

/** The shelf board, with the collection's name set into it. Tapping it opens the collection. */
@Composable
private fun Plank(title: String, palette: ShelfPalette, metrics: ShelfMetrics, onClick: () -> Unit) {
    val style = MaterialTheme.typography.headlineSmall.merge(
        TextStyle(shadow = Shadow(color = palette.engrave, offset = Offset(0f, 1.5f), blurRadius = 0f)),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = metrics.plankHeight)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(palette.plankTop, palette.plankFront, palette.plankBottom)))
                drawRect(palette.plankLip, size = Size(size.width, 3.dp.toPx()))
                val line = 1.dp.toPx()
                drawRect(
                    Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(0f, size.height - line),
                    size = Size(size.width, line),
                )
            }
            .clickable(
                onClickLabel = stringResource(R.string.home_open_shelf, title),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { heading() }
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = style,
            color = palette.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShelfBook(
    book: LibraryBook,
    faceOut: Boolean,
    metrics: ShelfMetrics,
    onOpen: (Long) -> Unit,
    onDetails: (Long) -> Unit,
) {
    val look = remember(book.id, book.pageCount, book.fileSize, faceOut) { BookLooks.of(book, leanAllowed = !faceOut) }
    val interaction = remember { MutableInteractionSource() }
    val lift by rememberPressLift(interaction)
    val height = if (faceOut) metrics.bookHeight * 0.94f else metrics.bookHeight * look.heightFraction
    val width = if (faceOut) {
        height * COVER_ASPECT
    } else {
        androidx.compose.ui.unit.lerp(metrics.spineMin, metrics.spineMax, look.thickness)
    }
    val shape = if (faceOut) {
        RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 5.dp, bottomEnd = 5.dp)
    } else {
        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
    }

    Box(
        Modifier
            // A leaning book needs a little room so it does not rest on its neighbour.
            .padding(horizontal = if (look.tiltDegrees != 0f) 6.dp else 0.dp)
            .size(width, height)
            .graphicsLayer {
                rotationZ = look.tiltDegrees
                transformOrigin = TransformOrigin(if (look.tiltDegrees < 0f) 0f else 1f, 1f)
                translationY = -lift * 6.dp.toPx()
                scaleX = 1f + lift * 0.03f
                scaleY = 1f + lift * 0.03f
                alpha = if (book.missing) 0.55f else 1f
            }
            .shadow(if (faceOut) 4.dp else 2.dp, shape, clip = false)
            .bookClicks(book, interaction, onOpen, onDetails)
            .clip(shape),
    ) {
        if (faceOut) FaceOutCover(book) else Spine(book, look)
    }
}

/** A book standing face out: its real cover, a hinge shadow, and how far you've read. */
@Composable
private fun FaceOutCover(book: LibraryBook) {
    Box(Modifier.fillMaxSize()) {
        BookCover(
            title = book.title,
            coverPath = book.coverPath,
            format = book.format,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
        )
        Box(
            Modifier
                .fillMaxHeight()
                .width(7.dp)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent))),
        )
        if (book.progress > 0.005f && book.progress < 0.995f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(book.progress)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * A book seen from its spine: a strip of its cover (or its generated colour), shaded like a
 * rounded spine, with the title running down it.
 */
@Composable
private fun Spine(book: LibraryBook, look: BookLook) {
    val colors = remember(book.title) { coverPalette(book.title) }
    val hasCover = remember(book.coverPath) { book.coverPath != null && File(book.coverPath).exists() }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.first()),
    ) {
        if (hasCover) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(book.coverPath!!)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val rounding = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0.22f),
                        0.2f to Color.Transparent,
                        0.7f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.36f),
                    )
                    // Covers vary wildly; a steady scrim keeps the title readable on any of them.
                    val scrim = Color.Black.copy(alpha = if (hasCover) 0.4f else 0.1f)
                    val gilt = Color(0xFFE9D3A3).copy(alpha = 0.75f)
                    val thin = 1.2.dp.toPx()
                    onDrawBehind {
                        drawRect(scrim)
                        drawRect(rounding)
                        when (look.band) {
                            0 -> {
                                drawRect(gilt, Offset(0f, size.height * 0.07f), Size(size.width, thin))
                                drawRect(gilt, Offset(0f, size.height * 0.07f + thin * 2.5f), Size(size.width, thin))
                                drawRect(gilt, Offset(0f, size.height * 0.9f), Size(size.width, thin))
                            }
                            1 -> drawRect(
                                gilt.copy(alpha = 0.35f),
                                Offset(0f, size.height * 0.08f),
                                Size(size.width, size.height * 0.07f),
                            )
                            else -> Unit
                        }
                    }
                },
        )
        Text(
            text = book.title,
            color = Color.White.copy(alpha = 0.95f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(vertical = 12.dp)
                .readDownward(),
        )
    }
}

/** Lays text out along the height of its box, reading top to bottom like an English spine. */
private fun Modifier.readDownward(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth),
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = -(placeable.width - placeable.height) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = 90f }
    }
}

/** A book rises slightly while pressed, instead of a ripple across its cover. */
@Composable
private fun rememberPressLift(interaction: MutableInteractionSource): State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    return animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 1f else 0f,
        animationSpec = tween(if (pressed) 90 else 160),
        label = "book-lift",
    )
}

/** Tap opens the book in its reader; long press opens its details. Spoken as one button. */
@Composable
private fun Modifier.bookClicks(
    book: LibraryBook,
    interaction: MutableInteractionSource,
    onOpen: (Long) -> Unit,
    onDetails: (Long) -> Unit,
): Modifier {
    val description = bookDescription(book)
    val detailsLabel = stringResource(R.string.home_book_details)
    val reduceMotion = LocalReduceMotion.current
    return this
        .combinedClickable(
            interactionSource = interaction,
            // With motion reduced there is no lift, so keep the usual press feedback.
            indication = if (reduceMotion) LocalIndication.current else null,
            onLongClickLabel = detailsLabel,
            onLongClick = { onDetails(book.id) },
            onClick = { onOpen(book.id) },
        )
        .clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick(label = null) { onOpen(book.id); true }
            onLongClick(label = detailsLabel) { onDetails(book.id); true }
        }
}

@Composable
private fun bookDescription(book: LibraryBook): String {
    val parts = mutableListOf(book.title)
    book.author?.takeIf { it.isNotBlank() }?.let { parts += stringResource(R.string.home_book_by, it) }
    val percent = Format.percent(book.progress)
    if (percent in 1..99) parts += stringResource(R.string.home_book_read, percent)
    if (book.missing) parts += stringResource(R.string.home_book_missing)
    return parts.joinToString(", ")
}
```

### 19. `app/src/main/java/app/folio/ui/screens/home/HomeMusicCard.kt`

**New.** A card driven by the existing `MusicPlayer`: artwork, title/artist, previous / play-pause / next and progress.
- Tapping the card opens the full player sheet.
- The **add-to-playlist** button opens *Reading music*; the list button opens the music library.
- When nothing is playing, it's a slim card that opens *Reading music* when tapped.

Full source:

```kotlin
package app.folio.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.ui.LocalContainer
import app.folio.ui.components.ProgressBar
import app.folio.ui.screens.music.PlayerSheet
import app.folio.ui.theme.LocalReduceMotion
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.io.File

/**
 * The home screen's music player. It drives the app's one music player: tapping it opens the full
 * player sheet, the add-to-playlist button opens "Reading music" (music for a book or a whole
 * collection, and new playlists), and the list button opens the music library. With nothing
 * queued it becomes a slim card that opens "Reading music".
 */
@Composable
fun HomeMusicCard(
    onOpenMusicLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = LocalContainer.current.musicPlayer
    val now by player.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(false) }
    var assigning by rememberSaveable { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current

    if (assigning) {
        MusicAssignSheet(onDismiss = { assigning = false }, onOpenMusicLibrary = onOpenMusicLibrary)
    }

    if (!now.hasQueue) {
        IdleMusicCard(
            onAssign = { assigning = true },
            onOpenMusicLibrary = onOpenMusicLibrary,
            modifier = modifier,
        )
        return
    }

    Surface(
        onClick = { sheet = true },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicArtwork(now.coverPath, size = 76.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 2.dp)) {
                        Text(
                            text = now.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = now.artist ?: stringResource(R.string.home_music_unknown_artist),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { assigning = true }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = stringResource(R.string.music_assign_open),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onOpenMusicLibrary) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.home_music_library),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = player::previous) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.music_previous))
                    }
                    FilledTonalIconButton(onClick = player::toggle) {
                        Crossfade(
                            targetState = now.isPlaying,
                            animationSpec = if (reduceMotion) snap() else tween(160),
                            label = "play-pause",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(
                                    if (playing) R.string.music_pause else R.string.music_play,
                                ),
                            )
                        }
                    }
                    IconButton(onClick = player::next) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.music_next))
                    }
                }
                val progress = if (now.durationMs > 0) now.positionMs.toFloat() / now.durationMs else 0f
                ProgressBar(progress, Modifier.fillMaxWidth().padding(end = 6.dp), height = 3.dp)
            }
        }
    }

    if (sheet) PlayerSheet(onDismiss = { sheet = false })
}

/** Nothing playing: tap to set reading music; the list button opens the music library. */
@Composable
private fun IdleMusicCard(onAssign: () -> Unit, onOpenMusicLibrary: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onAssign,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicArtwork(coverPath = null, size = 44.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_music_idle_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_music_idle_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenMusicLibrary) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.home_music_library),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A track's cover, or a quiet placeholder in the accent colour. */
@Composable
private fun MusicArtwork(coverPath: String?, size: Dp) {
    val exists = remember(coverPath) { coverPath != null && File(coverPath).exists() }
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.55f))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (exists) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(coverPath!!)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}
```

### 20. `app/src/main/java/app/folio/ui/screens/home/MusicAssignSheet.kt`

**New. The *Reading music* sheet.**
1. Choose a **book** (PDF/EPUB, searchable, the ones you're reading first) or a **collection**. A ♪ marks items that already have music.
2. Tick **playlists** and **songs**, or add the song playing now. **New playlist** makes one from the ticked songs, and it replaces them in the choice.
3. Set **Shuffle** and **Play when the book is opened**, then **Save**, **Save and play** (books only) or **Remove**.

Full source:

```kotlin
package app.folio.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.BookMusicMode
import app.folio.core.model.MusicSwitchPolicy
import app.folio.data.db.BookMusicSourceEntity
import app.folio.data.db.TrackEntity
import app.folio.data.repo.BookMusicSettings
import app.folio.data.repo.CollectionMusicSettings
import app.folio.ui.LocalContainer
import app.folio.ui.components.BookCover
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/** What reading music is being set for: one book, or a whole collection (a shelf). */
private sealed interface MusicTarget {
    val id: Long
    val name: String

    data class Book(override val id: Long, override val name: String) : MusicTarget
    data class Shelf(override val id: Long, override val name: String) : MusicTarget
}

/**
 * The home player's "Reading music" sheet: pick a book or a collection, then the playlists and
 * songs it plays when opened. A collection's music is played by its books that have none of
 * their own. New playlists can be made right here from the songs you tick.
 */
@Composable
fun MusicAssignSheet(onDismiss: () -> Unit, onOpenMusicLibrary: () -> Unit) {
    var target by remember { mutableStateOf<MusicTarget?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val chosen = target
        if (chosen == null) {
            TargetPicker(onPick = { target = it })
        } else {
            MusicPicker(
                target = chosen,
                onBack = { target = null },
                onDone = onDismiss,
                onOpenMusicLibrary = onOpenMusicLibrary,
            )
        }
    }
}

@Composable
private fun TargetPicker(onPick: (MusicTarget) -> Unit) {
    val container = LocalContainer.current
    val books by container.library.libraryBooks.collectAsStateWithLifecycle(emptyList())
    val collections by container.organization.collections.collectAsStateWithLifecycle(emptyList())
    val booksWithMusic by container.music.booksWithMusic.collectAsStateWithLifecycle(emptyList())
    val shelvesWithMusic by container.music.collectionsWithMusic.collectAsStateWithLifecycle(emptyList())
    var showShelves by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().lowercase(Locale.ROOT)

    // Reading music plays for PDF and EPUB books; list what you're reading first.
    val bookRows = remember(books, needle) {
        books
            .filter { MusicSwitchPolicy.supports(it.format.family) }
            .filter {
                needle.isEmpty() ||
                    it.title.lowercase(Locale.ROOT).contains(needle) ||
                    it.author?.lowercase(Locale.ROOT)?.contains(needle) == true
            }
            .sortedWith(compareByDescending<app.folio.core.model.LibraryBook> { it.lastOpenedAt ?: 0L }.thenBy { it.sortTitle })
    }
    val shelfRows = remember(collections, needle) {
        collections.filter { needle.isEmpty() || it.collection.name.lowercase(Locale.ROOT).contains(needle) }
    }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.music_assign_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.music_assign_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showShelves,
                onClick = { showShelves = false },
                label = { Text(stringResource(R.string.music_assign_books)) },
            )
            FilterChip(
                selected = showShelves,
                onClick = { showShelves = true },
                label = { Text(stringResource(R.string.music_assign_collections)) },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.action_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        if (!showShelves) {
            if (bookRows.isEmpty()) {
                item { EmptyLine(stringResource(R.string.music_assign_no_books)) }
            }
            items(bookRows, key = { it.id }) { book ->
                val hasMusic = book.id in booksWithMusic
                ListItem(
                    headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            text = if (hasMusic) {
                                stringResource(R.string.music_assign_has_music)
                            } else {
                                book.author ?: book.format.label
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        BookCover(
                            title = book.title,
                            coverPath = book.coverPath,
                            format = book.format,
                            modifier = Modifier
                                .width(34.dp)
                                .aspectRatio(0.68f),
                            shape = RoundedCornerShape(4.dp),
                        )
                    },
                    trailingContent = { if (hasMusic) MusicMark() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onPick(MusicTarget.Book(book.id, book.title)) },
                )
            }
            item { EmptyLine(stringResource(R.string.music_assign_formats_note)) }
        } else {
            if (shelfRows.isEmpty()) {
                item { EmptyLine(stringResource(R.string.music_assign_no_collections)) }
            }
            items(shelfRows, key = { it.collection.id }) { entry ->
                val hasMusic = entry.collection.id in shelvesWithMusic
                ListItem(
                    headlineContent = { Text(entry.collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            if (hasMusic) {
                                stringResource(R.string.music_assign_has_music)
                            } else {
                                pluralStringResource(R.plurals.library_books_count, entry.bookCount, entry.bookCount)
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null) },
                    trailingContent = { if (hasMusic) MusicMark() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable {
                        onPick(MusicTarget.Shelf(entry.collection.id, entry.collection.name))
                    },
                )
            }
            item { EmptyLine(stringResource(R.string.music_assign_collection_note)) }
        }
    }
}

@Composable
private fun MusicPicker(
    target: MusicTarget,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenMusicLibrary: () -> Unit,
) {
    val container = LocalContainer.current
    val music = container.music
    val scope = rememberCoroutineScope()
    val playlists by music.collections.collectAsStateWithLifecycle(emptyList())
    val tracks by music.tracks.collectAsStateWithLifecycle(emptyList())
    val now by container.musicPlayer.state.collectAsStateWithLifecycle()

    val playlistIds = remember(target) { mutableStateListOf<Long>() }
    val trackIds = remember(target) { mutableStateListOf<Long>() }
    var selection by remember(target) { mutableStateOf(emptyList<Long>()) }
    var mode by remember(target) { mutableStateOf(BookMusicMode.LOOP) }
    var autoplay by remember(target) { mutableStateOf(true) }
    var hadMusic by remember(target) { mutableStateOf(false) }
    var songQuery by rememberSaveable { mutableStateOf("") }
    var creatingPlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        when (target) {
            is MusicTarget.Book -> music.observeBookMusic(target.id).first()?.let { saved ->
                hadMusic = saved.sources.isNotEmpty()
                mode = saved.mode
                autoplay = saved.autoplay
                selection = saved.selection
                playlistIds += saved.sources.mapNotNull { it.collectionId }
                trackIds += saved.sources.mapNotNull { it.trackId }
            }
            is MusicTarget.Shelf -> music.observeCollectionMusic(target.id).first()?.let { saved ->
                hadMusic = saved.playlistIds.isNotEmpty() || saved.trackIds.isNotEmpty()
                mode = saved.mode
                autoplay = saved.autoplay
                playlistIds += saved.playlistIds
                trackIds += saved.trackIds
            }
        }
    }

    fun save(playAfter: Boolean) {
        scope.launch {
            val empty = playlistIds.isEmpty() && trackIds.isEmpty()
            when (target) {
                is MusicTarget.Book -> if (empty) {
                    music.clearBookMusic(target.id)
                } else {
                    val sources = playlistIds.map { BookMusicSourceEntity(bookId = target.id, collectionId = it, position = 0) } +
                        trackIds.map { BookMusicSourceEntity(bookId = target.id, trackId = it, position = 0) }
                    music.saveBookMusic(target.id, BookMusicSettings(mode, autoplay, sources, selection))
                    if (playAfter) container.bookMusic.playBook(target.id)
                }
                is MusicTarget.Shelf -> if (empty) {
                    music.clearCollectionMusic(target.id)
                } else {
                    music.saveCollectionMusic(
                        target.id,
                        CollectionMusicSettings(mode, autoplay, playlistIds.toList(), trackIds.toList()),
                    )
                }
            }
            onDone()
        }
    }

    val needle = songQuery.trim().lowercase(Locale.ROOT)
    val songRows = remember(tracks, needle) {
        tracks.filter {
            needle.isEmpty() ||
                it.displayTitle.lowercase(Locale.ROOT).contains(needle) ||
                it.displayArtist?.lowercase(Locale.ROOT)?.contains(needle) == true
        }
    }

    Row(
        Modifier.padding(start = 8.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (target is MusicTarget.Book) R.string.music_assign_for_book else R.string.music_assign_for_collection,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = target.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (tracks.isEmpty()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.music_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.music_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 12.dp))
            Button(onClick = { onDone(); onOpenMusicLibrary() }) { Text(stringResource(R.string.home_music_library)) }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp),
    ) {
        val playingId = now.trackId
        if (playingId != null && playingId !in trackIds) {
            item(key = "now-playing") {
                AssistChip(
                    onClick = { trackIds += playingId },
                    label = {
                        Text(
                            stringResource(R.string.music_assign_use_playing, now.title.orEmpty()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item(key = "playlists-header") {
            SheetSectionHeader(
                title = stringResource(R.string.music_assign_playlists),
                action = stringResource(R.string.music_assign_new_playlist),
                onAction = { creatingPlaylist = true },
            )
        }
        if (playlists.isEmpty()) {
            item(key = "no-playlists") { EmptyLine(stringResource(R.string.music_assign_no_playlists)) }
        }
        items(playlists, key = { "p" + it.collection.id }) { entry ->
            CheckRow(
                checked = entry.collection.id in playlistIds,
                onToggle = { playlistIds.toggle(entry.collection.id) },
                title = entry.collection.name,
                subtitle = pluralStringResource(R.plurals.music_track_count, entry.trackCount, entry.trackCount),
                icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            )
        }

        item(key = "songs-header") {
            SheetSectionHeader(title = stringResource(R.string.music_tab_tracks))
            OutlinedTextField(
                value = songQuery,
                onValueChange = { songQuery = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.music_search)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        items(songRows, key = { "t" + it.id }) { track ->
            CheckRow(
                checked = track.id in trackIds,
                onToggle = { trackIds.toggle(track.id) },
                title = track.displayTitle,
                subtitle = track.displayArtist,
                icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                enabled = !track.missing,
            )
        }
    }

    HorizontalDivider()
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (mode == BookMusicMode.SELECTION) {
            Text(
                text = stringResource(R.string.music_assign_selection_kept),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SwitchLine(
            label = stringResource(R.string.music_mode_shuffle),
            checked = mode == BookMusicMode.SHUFFLE,
            onChange = { mode = if (it) BookMusicMode.SHUFFLE else BookMusicMode.LOOP },
        )
        SwitchLine(
            label = stringResource(R.string.music_assign_autoplay),
            checked = autoplay,
            onChange = { autoplay = it },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hadMusic) {
                TextButton(onClick = {
                    playlistIds.clear()
                    trackIds.clear()
                    save(playAfter = false)
                }) { Text(stringResource(R.string.music_remove_all)) }
            }
            Spacer(Modifier.weight(1f))
            val chosen = playlistIds.isNotEmpty() || trackIds.isNotEmpty()
            if (target is MusicTarget.Book) {
                OutlinedButton(onClick = { save(playAfter = true) }, enabled = chosen) {
                    Text(stringResource(R.string.music_assign_save_play))
                }
            }
            Button(onClick = { save(playAfter = false) }, enabled = chosen || hadMusic) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (creatingPlaylist) {
        NewPlaylistDialog(
            tracks = tracks.filterNot { it.missing },
            preselected = trackIds.toList(),
            onDismiss = { creatingPlaylist = false },
            onCreate = { name, ids ->
                creatingPlaylist = false
                scope.launch {
                    val id = music.createPlaylist(name, ids)
                    // The new playlist replaces the loose songs it was made from.
                    playlistIds += id
                    trackIds.removeAll(ids)
                }
            },
        )
    }
}

/** Name a playlist and pick its songs; the songs already ticked in the sheet start ticked. */
@Composable
private fun NewPlaylistDialog(
    tracks: List<TrackEntity>,
    preselected: List<Long>,
    onDismiss: () -> Unit,
    onCreate: (String, List<Long>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>().apply { addAll(preselected) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.music_assign_new_playlist)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.music_assign_playlist_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picked.toggle(track.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = track.id in picked, onCheckedChange = { picked.toggle(track.id) })
                            Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), picked.toList()) },
                enabled = name.isNotBlank() && picked.isNotEmpty(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SheetSectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(action)
            }
        }
    }
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onToggle: () -> Unit,
    title: String,
    subtitle: String?,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        leadingContent = icon,
        trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(enabled = enabled, onClick = onToggle),
    )
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MusicMark() {
    Icon(
        Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

private fun SnapshotStateList<Long>.toggle(id: Long) {
    if (id in this) remove(id) else add(id)
}
```

### 21. `app/src/main/java/app/folio/ui/components/BookCover.kt`

`coverPalette()` made `internal` so spines use the same colours as generated covers.

Diff:

```diff
@@ -103,7 +103,7 @@ private fun GeneratedCover(title: String, format: BookFormat) {
     }
 }
 
-private fun coverPalette(seed: String): List<Color> {
+internal fun coverPalette(seed: String): List<Color> {
     val hash = seed.hashCode().absoluteValue
     val palettes = listOf(
         listOf(Color(0xFF2F4858), Color(0xFF33658A)),
```

## Library, settings and resources

### 22. `app/src/main/java/app/folio/ui/screens/library/LibraryScreen.kt`

Label for the new smart collection.

Diff:

```diff
@@ -478,6 +478,7 @@ fun SmartCollection.label(): String = stringResource(
         SmartCollection.FAVORITES -> R.string.smart_favorites
         SmartCollection.LONG_BOOKS -> R.string.smart_long_books
         SmartCollection.SHORT_READS -> R.string.smart_short_reads
+        SmartCollection.UNSORTED -> R.string.smart_unsorted
     },
 )
 
```

### 23. `app/src/main/java/app/folio/ui/screens/settings/SettingsScreens.kt`

Settings → Library gets "Home screen" (Bookshelf / Classic).

Diff:

```diff
@@ -425,6 +425,26 @@ fun LibrarySettingsScreen(
     val settings by viewModel.settings.collectAsStateWithLifecycle()
 
     SettingsScaffold(stringResource(R.string.settings_library), onBack) {
+        SectionHeader(stringResource(R.string.home_style))
+        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
+            app.folio.data.settings.HomeStyle.entries.forEach { style ->
+                FilterChip(
+                    selected = settings.home.style == style,
+                    onClick = { viewModel.update { it.copy(home = it.home.copy(style = style)) } },
+                    label = {
+                        Text(
+                            stringResource(
+                                when (style) {
+                                    app.folio.data.settings.HomeStyle.BOOKSHELF -> R.string.home_style_bookshelf
+                                    app.folio.data.settings.HomeStyle.CLASSIC -> R.string.home_style_classic
+                                },
+                            ),
+                        )
+                    },
+                )
+            }
+        }
+
         SectionHeader(stringResource(R.string.library_layout))
         FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
             LibraryLayout.entries.forEach { layout ->
```

### 24. `app/src/main/res/values/strings.xml`

New strings for the home screen, clock, music card, the *Reading music* sheet and settings.

Diff:

```diff
@@ -583,4 +583,51 @@
     <string name="drawing_delete_message">This drawing will be removed from Folio. This can\'t be undone.</string>
     <string name="notes_tab_drawings">Drawings</string>
     <string name="empty_drawings">No drawings yet. In an EPUB or text book, tap Draw while reading.</string>
+
+    <!-- Bookshelf home -->
+    <string name="smart_unsorted">Not on a shelf</string>
+    <string name="home_style">Home screen</string>
+    <string name="home_style_bookshelf">Bookshelf</string>
+    <string name="home_style_classic">Classic</string>
+    <string name="home_other_books">Other books</string>
+    <string name="home_shelf_empty">Empty shelf. Add books to this collection from the library.</string>
+    <string name="home_first_shelf">Your first shelf</string>
+    <string name="home_first_shelf_hint">Create a collection and it becomes a shelf here.</string>
+    <string name="home_open_shelf">Open %1$s</string>
+    <string name="home_book_details">Book details</string>
+    <string name="home_book_by">by %1$s</string>
+    <string name="home_book_read">%1$d%% read</string>
+    <string name="home_book_missing">file missing</string>
+    <string name="home_clock_timer">Focus timer</string>
+    <string name="home_clock_focus">Focus %1$s</string>
+    <string name="home_clock_break">Break %1$s</string>
+    <string name="home_clock_paused">Paused %1$s</string>
+    <string name="home_clock_description">%1$s. %2$s. Opens the focus timer</string>
+    <string name="home_your_library">Collections, categories and tags</string>
+    <string name="home_music_idle_title">Reading music</string>
+    <string name="home_music_idle_message">Choose music for a book or a collection</string>
+    <string name="home_music_library">Music library</string>
+    <string name="home_music_unknown_artist">Unknown artist</string>
+
+    <!-- Reading music sheet (home player) -->
+    <string name="music_assign_title">Reading music</string>
+    <string name="music_assign_subtitle">Choose a book or a collection, then the music it plays when you open it.</string>
+    <string name="music_assign_open">Set reading music for a book or collection</string>
+    <string name="music_assign_books">Books</string>
+    <string name="music_assign_collections">Collections</string>
+    <string name="music_assign_no_books">No PDF or EPUB books found.</string>
+    <string name="music_assign_no_collections">No collections found. Create one and it becomes a shelf.</string>
+    <string name="music_assign_has_music">Has reading music</string>
+    <string name="music_assign_formats_note">Reading music plays for PDF and EPUB books.</string>
+    <string name="music_assign_collection_note">Books in a collection play its music unless they have music of their own.</string>
+    <string name="music_assign_for_book">Music for the book</string>
+    <string name="music_assign_for_collection">Music for the collection</string>
+    <string name="music_assign_use_playing">Add \"%1$s\" (playing now)</string>
+    <string name="music_assign_playlists">Playlists</string>
+    <string name="music_assign_new_playlist">New playlist</string>
+    <string name="music_assign_no_playlists">No playlists yet. Tick some songs below, then tap New playlist.</string>
+    <string name="music_assign_playlist_name">Playlist name</string>
+    <string name="music_assign_selection_kept">This book plays a chosen selection, set in its details. Turning on shuffle replaces it.</string>
+    <string name="music_assign_autoplay">Play when the book is opened</string>
+    <string name="music_assign_save_play">Save and play</string>
 </resources>
```

## Tests

### 25. `app/src/test/java/app/folio/core/model/BookshelfTest.kt`

**New.** 8 tests: shelves follow collection order, unsorted books are ordered newest first, stale links are ignored, the reading shelf's rules, the empty library, looks are stable and in range, thickness follows page count, and leaning only happens when allowed.

Full source:

```kotlin
package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfTest {

    private val now = 1_800_000_000_000L

    private fun book(
        id: Long,
        added: Long = id,
        status: ReadingStatus = ReadingStatus.UNREAD,
        progress: Float = 0f,
        lastOpened: Long? = null,
        pages: Int? = 200,
        missing: Boolean = false,
    ) = LibraryBook(
        id = id,
        uri = "content://book/$id",
        title = "Book $id",
        author = null,
        series = null,
        volume = null,
        year = null,
        fileName = "book$id.pdf",
        format = BookFormat.PDF,
        status = status,
        favorite = false,
        progress = progress,
        pageCount = pages,
        fileSize = 1_000_000,
        dateAdded = added,
        lastOpenedAt = lastOpened,
        finishedAt = null,
        categoryId = null,
        categoryName = null,
        tags = emptyList(),
        collectionIds = emptySet(),
        coverPath = null,
        missing = missing,
        customOrder = 0,
        sortTitle = "book $id",
    )

    @Test
    fun everyCollectionBecomesAShelfInItsOwnOrder() {
        val books = (1L..5L).map { book(it) }
        val shelf = BookshelfBuilder.build(
            books = books,
            collections = listOf(ShelfCollection(10, "Science"), ShelfCollection(20, "Empty")),
            links = listOf(ShelfLink(10, 3, 1), ShelfLink(10, 1, 0), ShelfLink(10, 5, 2)),
            now = now,
        )
        assertEquals(listOf("Science", "Empty"), shelf.shelves.map { it.name })
        assertEquals(listOf(1L, 3L, 5L), shelf.shelves[0].books.map { it.id })
        assertTrue(shelf.shelves[1].books.isEmpty())
    }

    @Test
    fun booksOnNoShelfAreUnsortedNewestFirst() {
        val books = listOf(book(1, added = 100), book(2, added = 300), book(3, added = 200))
        val shelf = BookshelfBuilder.build(
            books = books,
            collections = listOf(ShelfCollection(10, "Science")),
            links = listOf(ShelfLink(10, 2, 0)),
            now = now,
        )
        assertEquals(listOf(3L, 1L), shelf.unsorted.map { it.id })
    }

    @Test
    fun linksToRemovedBooksOrUnknownCollectionsAreIgnored() {
        val shelf = BookshelfBuilder.build(
            books = listOf(book(1)),
            collections = listOf(ShelfCollection(10, "Science")),
            links = listOf(ShelfLink(10, 99, 0), ShelfLink(77, 1, 0)),
            now = now,
        )
        assertTrue(shelf.shelves.single().books.isEmpty())
        // A link to a collection that no longer exists does not hide the book.
        assertEquals(listOf(1L), shelf.unsorted.map { it.id })
    }

    @Test
    fun readingShelfHoldsStartedBooksMostRecentFirst() {
        val books = listOf(
            book(1, status = ReadingStatus.READING, lastOpened = 10),
            book(2, progress = 0.4f, lastOpened = 30),
            book(3, status = ReadingStatus.FINISHED, progress = 1f, lastOpened = 40),
            book(4),
            book(5, status = ReadingStatus.READING, lastOpened = 50, missing = true),
        )
        val shelf = BookshelfBuilder.build(books, emptyList(), emptyList(), now)
        assertEquals(listOf(2L, 1L), shelf.reading.map { it.id })
    }

    @Test
    fun emptyLibraryIsEmpty() {
        assertTrue(BookshelfBuilder.build(emptyList(), listOf(ShelfCollection(1, "A")), emptyList(), now).isEmpty)
    }

    @Test
    fun looksAreStableAndInRange() {
        (1L..500L).forEach { id ->
            val look = BookLooks.of(book(id))
            assertEquals(look, BookLooks.of(book(id)))
            assertTrue(look.heightFraction in 0.8f..1.0f)
            assertTrue(look.thickness in 0f..1f)
            assertTrue(look.band in 0 until BookLooks.BANDS)
        }
    }

    @Test
    fun thickerBooksHaveMorePages() {
        val slim = BookLooks.of(book(1, pages = 60)).thickness
        val thick = BookLooks.of(book(1, pages = 900)).thickness
        assertTrue(thick > slim)
    }

    @Test
    fun onlySomeBooksLeanAndNeverWhenNotAllowed() {
        val leaning = (1L..700L).count { BookLooks.of(book(it)).tiltDegrees != 0f }
        assertTrue(leaning in 50..160)
        assertTrue((1L..700L).all { BookLooks.of(book(it), leanAllowed = false).tiltDegrees == 0f })
    }
}
```

### 26. `app/src/test/java/app/folio/core/model/MusicInheritanceTest.kt`

**New.** 4 tests: a book's own music wins; a book without music uses its first collection's soundtrack; empty plans count as no music; no music anywhere gives null.

Full source:

```kotlin
package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicInheritanceTest {

    private fun plan(vararg trackIds: Long, autoplay: Boolean = true) = MusicPlan(
        mode = BookMusicMode.LOOP,
        autoplay = autoplay,
        sources = trackIds.map { MusicSourceRef(collectionId = null, trackId = it) },
    )

    @Test
    fun bookMusicWinsOverItsCollections() {
        val own = plan(1)
        assertEquals(own, MusicInheritance.effective(own, listOf(plan(2), plan(3))))
    }

    @Test
    fun bookWithoutMusicPlaysItsFirstCollectionSoundtrack() {
        val science = plan(2)
        assertEquals(science, MusicInheritance.effective(null, listOf(science, plan(3))))
    }

    @Test
    fun emptyPlansCountAsNoMusic() {
        val history = plan(3, autoplay = false)
        assertEquals(history, MusicInheritance.effective(plan(), listOf(plan(), history)))
    }

    @Test
    fun noMusicAnywhereIsNull() {
        assertNull(MusicInheritance.effective(null, emptyList()))
        assertNull(MusicInheritance.effective(plan(), listOf(plan())))
    }
}
```

## Build, release and docs

### 27. `app/build.gradle.kts`

Version 1.4.0 (versionCode 14).

Diff:

```diff
@@ -24,8 +24,8 @@ android {
         applicationId = "app.folio.reader"
         minSdk = 26
         targetSdk = 36
-        versionCode = 13
-        versionName = "1.3.0"
+        versionCode = 14
+        versionName = "1.4.0"
         testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
     }
 
```

### 28. `.github/workflows/release.yml`

**New. The Release APK workflow.**
- Runs on manual dispatch or on a `v*` tag.
- Sets up JDK 21, the Android SDK (`setup-android@v4`, from Copilot's PR #3) and Gradle.
- Restores the signing key from secrets and runs the unit tests.
- Builds a signed `assembleRelease`, or a debug APK when there are no secrets.
- Uploads the `folio-apk` artifact, and on tags publishes a GitHub release.

Full source:

```yaml
name: Release APK

# Builds a signed release APK.
# - Run it by hand: Actions → "Release APK" → Run workflow. The APK is attached to the run.
# - Push a tag such as v1.4.0: the APK is also published as a GitHub release.
#
# Signing uses four repository secrets (Settings → Secrets and variables → Actions):
#   KEYSTORE_BASE64    the .jks file, base64-encoded (base64 -w0 folio.jks)
#   KEYSTORE_PASSWORD  store password
#   KEY_ALIAS          key alias
#   KEY_PASSWORD       key password
# Use the same keystore as the copy installed on your phone, or Android refuses the update.
# Without the secrets the workflow still builds, but only a debug-signed APK.

on:
  workflow_dispatch:
  push:
    tags: ["v*"]

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - uses: android-actions/setup-android@v4

      - uses: gradle/actions/setup-gradle@v4

      - name: Restore signing key
        id: signing
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          if [ -z "$KEYSTORE_BASE64" ]; then
            echo "::warning::No signing secrets set; building a debug-signed APK instead."
            echo "signed=false" >> "$GITHUB_OUTPUT"
            exit 0
          fi
          echo "$KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/release.jks"
          {
            echo "storeFile=$RUNNER_TEMP/release.jks"
            echo "storePassword=$KEYSTORE_PASSWORD"
            echo "keyAlias=$KEY_ALIAS"
            echo "keyPassword=$KEY_PASSWORD"
          } > keystore.properties
          echo "signed=true" >> "$GITHUB_OUTPUT"

      - name: Unit tests
        run: ./gradlew :app:testDebugUnitTest

      - name: Build release APK
        if: steps.signing.outputs.signed == 'true'
        run: |
          ./gradlew :app:assembleRelease
          mkdir -p dist
          cp app/build/outputs/apk/release/app-release.apk "dist/Folio-${GITHUB_REF_NAME}.apk"

      - name: Build debug APK (no signing secrets)
        if: steps.signing.outputs.signed != 'true'
        run: |
          ./gradlew :app:assembleDebug
          mkdir -p dist
          cp app/build/outputs/apk/debug/app-debug.apk "dist/Folio-${GITHUB_REF_NAME}-debug.apk"

      - uses: actions/upload-artifact@v4
        with:
          name: folio-apk
          path: dist/*.apk

      - name: Publish GitHub release
        if: startsWith(github.ref, 'refs/tags/')
        uses: softprops/action-gh-release@v2
        with:
          files: dist/*.apk
          generate_release_notes: true

      - name: Remove signing key
        if: always()
        run: rm -f keystore.properties "$RUNNER_TEMP/release.jks"
```

### 29. `README.md`

Describes the bookshelf home and collection soundtracks.

Diff:

```diff
@@ -81,6 +81,11 @@ force close or a dead battery cannot lose your place.
 - **Library:** 5 layouts, grouping, combinable filters, sorting, bulk editing, long-press actions,
   categories, collections (ordered, drag to reorder), tags with normalisation and autocomplete,
   smart collections, reading queue, favourites, recently removed (restorable)
+- **Home:** a bookshelf built from your collections: a "Currently reading" shelf, one shelf per
+  collection with its name set into the board, and at the bottom an "Other books" shelf holding the
+  books that are in no collection. Books show their real covers and spines, thickness follows page
+  count. A live clock opens the focus timer and the music player sits above the shelves. The
+  original sectioned home is still available as "Classic" in Settings → Library.
 - **Readers:** PDF (continuous / paged / two-page, zoom, night mode, margin cropping, text
   selection, highlights, in-book search, thumbnails), EPUB (pagination or scroll, fonts, spacing,
   themes, selection, highlights, search, TOC), manga (LTR/RTL/vertical, double page, fits, zoom,
@@ -91,7 +96,9 @@ force close or a dead battery cannot lose your place.
 - **Music:** offline music library (MP3, M4A/AAC, FLAC, OGG, WAV) with library-only metadata edits,
   music tags and collections; background playback with notification and lock-screen controls; link
   collections or tracks to PDF/EPUB books (loop, shuffle or a chosen selection) so the book's music
-  plays while you read it; pauses during Pomodoro breaks
+  plays while you read it; give a whole book collection a soundtrack that its books play unless they
+  have music of their own; set all of this, and make new playlists, from "Reading music" on the home
+  player; pauses during Pomodoro breaks
 - **Annotations:** bookmarks, five highlight colours, notes, a global notes hub, Markdown export
 - **Scribble:** draw on PDF and comic pages with a pen, a see-through highlighter (adjustable
   opacity, 10–100 %) and a stroke eraser, with undo/redo. In EPUB and text books, Draw makes a
```

## Other changes

- `gradlew`: file mode changed from `100644` to `100755` so it runs on Linux CI runners.
- `docs/Folio-1.4.0-changes.md`, `docs/release.yml`: the earlier 1.4.0 change log and a reference copy of the workflow (PR #4). This document replaces the change log and covers everything since 1.3.0.

## Verification

- **JVM unit tests** (`:app:testDebugUnitTest`) pass on GitHub Actions runs #2 and #3, including `BookshelfTest` (8) and `MusicInheritanceTest` (4).
- **Signed release build** (`:app:assembleRelease`) passed on both runs. Run #3: https://github.com/darkwarrior2005/Folio-app-private/actions/runs/36544502067
- **Migration test** (`MigrationTest`, 3 → 4) is instrumented: it runs on a device, not in CI.
- **Not yet done:** testing on a real phone.

## Follow-ups

- **Commit the v4 schema file:** `app/schemas/app.folio.data.db.FolioDatabase/4.json` is created by the first local build. Commit it; the migration test needs it.
- **Bump the version before the next release:** `versionCode` 15, `versionName` 1.5.0 if 1.4.0 is already installed. Then tag `v1.5.0`.
