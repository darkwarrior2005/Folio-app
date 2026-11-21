package app.folio.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        CategoryEntity::class,
        CollectionEntity::class,
        CollectionBookEntity::class,
        TagEntity::class,
        BookTagEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        ReadingSessionEntity::class,
        PomodoroSessionEntity::class,
        ReadingGoalEntity::class,
        QueueItemEntity::class,
        BookTextFts::class,
        TrackEntity::class,
        MusicTagEntity::class,
        TrackTagEntity::class,
        MusicCollectionEntity::class,
        MusicCollectionTrackEntity::class,
        BookMusicEntity::class,
        BookMusicSourceEntity::class,
        BookMusicSelectionEntity::class,
        DrawnNoteEntity::class,
        InkStrokeEntity::class,
        CollectionMusicEntity::class,
        CollectionMusicSourceEntity::class,
        MangaEntity::class,
        MangaChapterEntity::class,
        MangaDownloadEntity::class,
        ExtensionRepoEntity::class,
        TrustedExtensionEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        // Collection soundtracks: two new tables, nothing existing changes.
        AutoMigration(from = 3, to = 4),
        // Manga library, extension repositories and trust: five new tables, nothing existing changes.
        AutoMigration(from = 4, to = 5),
    ],
)
abstract class FolioDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun tags(): TagDao
    abstract fun categories(): CategoryDao
    abstract fun collections(): CollectionDao
    abstract fun annotations(): AnnotationDao
    abstract fun reading(): ReadingDao
    abstract fun search(): SearchDao
    abstract fun music(): MusicDao
    abstract fun ink(): InkDao
    abstract fun manga(): MangaDao

    companion object {
        const val NAME = "folio.db"

        fun build(context: Context): FolioDatabase =
            Room.databaseBuilder(context, FolioDatabase::class.java, NAME)
                // WAL keeps progress writes cheap and crash-safe while the reader is open.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
