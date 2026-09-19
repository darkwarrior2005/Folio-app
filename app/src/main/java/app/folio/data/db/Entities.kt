package app.folio.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import app.folio.core.model.BookFormat
import app.folio.core.model.BookMetadata
import app.folio.core.model.HighlightColor
import app.folio.core.model.IndexState
import app.folio.core.model.ReadingStatus
import app.folio.core.model.StorageMode

@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("sortTitle"), Index("displayAuthor"), Index("categoryId"), Index("status"),
        Index("fileHash"), Index("dateAdded"), Index("lastOpenedAt"), Index("removedAt"), Index("uri"),
    ],
)
@Serializable
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** content:// URI (linked) or file:// URI (copied into app storage). */
    val uri: String,
    /** Physical file name. Never changed by metadata edits. */
    val fileName: String,
    val fileSize: Long,
    val fileHash: String,
    val mimeType: String?,
    val format: BookFormat,
    val storage: StorageMode = StorageMode.LINKED,
    val pageCount: Int? = null,
    @Embedded(prefix = "imp_") val imported: BookMetadata = BookMetadata(),
    @Embedded(prefix = "usr_") val overrides: BookMetadata = BookMetadata(),
    val importedCoverPath: String? = null,
    val customCoverPath: String? = null,
    /** User explicitly removed the cover: show the generated placeholder. */
    val coverHidden: Boolean = false,
    /** Denormalized resolved values, kept in sync by the repository, for sorting and search. */
    val displayTitle: String,
    val displayAuthor: String? = null,
    val displaySeries: String? = null,
    val sortTitle: String,
    val categoryId: Long? = null,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val statusManual: Boolean = false,
    val favorite: Boolean = false,
    val progress: Float = 0f,
    val dateAdded: Long,
    val lastOpenedAt: Long? = null,
    val finishedAt: Long? = null,
    val missing: Boolean = false,
    val passwordProtected: Boolean = false,
    val indexState: IndexState = IndexState.PENDING,
    /** Per-book reader overrides (e.g. manga direction) as JSON. */
    val readerPrefs: String? = null,
    val customOrder: Long = 0,
    /** Set when removed from the library (recoverable from "Recently removed"). */
    val removedAt: Long? = null,
)

@Entity(tableName = "categories", indices = [Index(value = ["nameKey"], unique = true)])
@Serializable
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "collections")
@Serializable
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Icon key from the built-in icon set, or an emoji. */
    val icon: String? = null,
    val colorArgb: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(
    tableName = "collection_books",
    primaryKeys = ["collectionId", "bookId"],
    foreignKeys = [
        ForeignKey(CollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId")],
)
@Serializable
data class CollectionBookEntity(
    val collectionId: Long,
    val bookId: Long,
    val position: Int,
    val addedAt: Long,
)

@Entity(tableName = "tags", indices = [Index(value = ["nameKey"], unique = true)])
@Serializable
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    val createdAt: Long,
)

@Entity(
    tableName = "book_tags",
    primaryKeys = ["bookId", "tagId"],
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
@Serializable
data class BookTagEntity(val bookId: Long, val tagId: Long)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
)
@Serializable
data class ReadingProgressEntity(
    @PrimaryKey val bookId: Long,
    /** Serialized [app.folio.core.model.BookLocation]. */
    val location: String,
    val progress: Float,
    val page: Int?,
    val pageCount: Int?,
    val updatedAt: Long,
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId"), Index("createdAt")],
)
@Serializable
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val location: String,
    /** Human label of the position, e.g. "Page 50" or "Chapter 3". */
    val positionLabel: String,
    val progress: Float,
    val title: String? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId"), Index("createdAt")],
)
@Serializable
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val location: String,
    val positionLabel: String,
    val progress: Float,
    val text: String,
    val color: HighlightColor = HighlightColor.YELLOW,
    val note: String? = null,
    /** Page/block index and character range for PDF and text readers. */
    val page: Int? = null,
    val rangeStart: Int? = null,
    val rangeEnd: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A free note pinned to a reading location (notes on highlights/bookmarks live on those rows). */
@Entity(
    tableName = "notes",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bookId"), Index("updatedAt")],
)
@Serializable
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val location: String,
    val positionLabel: String,
    val progress: Float,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "reading_sessions",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("bookId"), Index("startedAt")],
)
@Serializable
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long?,
    val startedAt: Long,
    val endedAt: Long,
    val durationMs: Long,
    val pagesRead: Int = 0,
    val startProgress: Float = 0f,
    val endProgress: Float = 0f,
    val pomodoroSessionId: Long? = null,
)

enum class PomodoroPhase { FOCUS, SHORT_BREAK, LONG_BREAK }

@Entity(tableName = "pomodoro_sessions", indices = [Index("startedAt")])
@Serializable
data class PomodoroSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phase: PomodoroPhase,
    val startedAt: Long,
    val endedAt: Long,
    val plannedMs: Long,
    val actualMs: Long,
    val completed: Boolean,
    val bookId: Long? = null,
)

enum class GoalType { MINUTES_PER_DAY, PAGES_PER_DAY, BOOKS_PER_MONTH, BOOKS_PER_YEAR }

@Entity(tableName = "reading_goals")
@Serializable
data class ReadingGoalEntity(
    @PrimaryKey val type: GoalType,
    val target: Int,
    val enabled: Boolean,
)

@Entity(
    tableName = "queue",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
)
@Serializable
data class QueueItemEntity(
    @PrimaryKey val bookId: Long,
    val position: Int,
    val addedAt: Long,
)

/** Full-text index of extracted book text, chunked by page / chapter segment. */
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61, notIndexed = ["bookId", "location", "label"])
@Entity(tableName = "book_text")
data class BookTextFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val bookId: Long,
    val location: String,
    val label: String,
    val text: String,
)
