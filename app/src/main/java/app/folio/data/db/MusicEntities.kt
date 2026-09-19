package app.folio.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.folio.core.model.BookMusicMode
import app.folio.core.model.TrackMetadata
import kotlinx.serialization.Serializable

@Entity(tableName = "tracks", indices = [Index("fileHash"), Index("sortTitle")])
@Serializable
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val fileName: String,
    val fileSize: Long,
    val fileHash: String,
    val mimeType: String?,
    val durationMs: Long,
    @Embedded(prefix = "imp_") val imported: TrackMetadata = TrackMetadata(),
    @Embedded(prefix = "usr_") val overrides: TrackMetadata = TrackMetadata(),
    val displayTitle: String,
    val displayArtist: String?,
    val sortTitle: String,
    val coverPath: String? = null,
    val missing: Boolean = false,
    val addedAt: Long,
)

@Entity(tableName = "music_tags", indices = [Index(value = ["nameKey"], unique = true)])
@Serializable
data class MusicTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String,
    val createdAt: Long,
)

@Entity(
    tableName = "track_tags",
    primaryKeys = ["trackId", "tagId"],
    foreignKeys = [
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MusicTagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
@Serializable
data class TrackTagEntity(val trackId: Long, val tagId: Long)

@Entity(tableName = "music_collections")
@Serializable
data class MusicCollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
)

@Entity(
    tableName = "music_collection_tracks",
    primaryKeys = ["collectionId", "trackId"],
    foreignKeys = [
        ForeignKey(MusicCollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("trackId")],
)
@Serializable
data class MusicCollectionTrackEntity(val collectionId: Long, val trackId: Long, val position: Int)

@Entity(
    tableName = "book_music",
    foreignKeys = [ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE)],
)
@Serializable
data class BookMusicEntity(
    @PrimaryKey val bookId: Long,
    val mode: BookMusicMode = BookMusicMode.LOOP,
    val autoplay: Boolean = true,
    val updatedAt: Long,
)

@Entity(
    tableName = "book_music_sources",
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MusicCollectionEntity::class, ["id"], ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("bookId"), Index("collectionId"), Index("trackId")],
)
@Serializable
data class BookMusicSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val collectionId: Long? = null,
    val trackId: Long? = null,
    val position: Int,
)

@Entity(
    tableName = "book_music_selection",
    primaryKeys = ["bookId", "trackId"],
    foreignKeys = [
        ForeignKey(BookEntity::class, ["id"], ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TrackEntity::class, ["id"], ["trackId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("trackId")],
)
@Serializable
data class BookMusicSelectionEntity(val bookId: Long, val trackId: Long, val position: Int)

data class MusicCollectionWithCount(
    @Embedded val collection: MusicCollectionEntity,
    val trackCount: Int,
)

data class MusicTagWithCount(
    @Embedded val tag: MusicTagEntity,
    val trackCount: Int,
)
