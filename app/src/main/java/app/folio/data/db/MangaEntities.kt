package app.folio.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * A manga as known to the library or browsed from a source. [sourceId] + [url] is the natural
 * key a source identifies a manga by; [url] is a source-relative path, not a full URL.
 */
@Entity(
    tableName = "manga",
    indices = [Index(value = ["sourceId", "url"], unique = true), Index("favorite")],
)
@Serializable
data class MangaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: String? = null,
    val status: Int = 0,
    val thumbnailUrl: String? = null,
    /** In the library, as opposed to only browsed/searched. */
    val favorite: Boolean = false,
    val initialized: Boolean = false,
    val updateStrategy: String = "ALWAYS_UPDATE",
    val dateAdded: Long = 0,
    val lastUpdate: Long = 0,
    /** The extension's own id(s) for this manga, as JSON. Opaque to Folio. */
    val memo: String? = null,
)

@Entity(
    tableName = "manga_chapters",
    foreignKeys = [
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mangaId", "url"], unique = true), Index("mangaId")],
)
@Serializable
data class MangaChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val pagesCount: Int = 0,
    val chapterNumber: Float = -1f,
    val dateUpload: Long = 0,
    val dateFetch: Long = 0,
    /** The order the source listed chapters in, kept for display when numbers are missing. */
    val sourceOrder: Int = 0,
    val memo: String? = null,
)

@Entity(
    tableName = "manga_downloads",
    foreignKeys = [
        ForeignKey(
            entity = MangaChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["chapterId"], unique = true), Index("mangaId")],
)
@Serializable
data class MangaDownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: Long,
    val chapterId: Long,
    val queuedAt: Long = 0,
)

@Entity(tableName = "extension_repos", indices = [Index(value = ["baseUrl"], unique = true)])
@Serializable
data class ExtensionRepoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val baseUrl: String,
    val name: String = "",
    val website: String? = null,
    val signingKeyFingerprint: String? = null,
)

@Entity(
    tableName = "trusted_extensions",
    indices = [Index(value = ["packageName", "versionCode", "signatureHash"], unique = true)],
)
@Serializable
data class TrustedExtensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val versionCode: Long,
    val signatureHash: String,
)
