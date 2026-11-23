package app.folio.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.folio.core.model.MangaDownloadStatus

/**
 * The manga library. It is deliberately separate from [BookEntity] and the rest of the book
 * tables: a manga is a series of chapters fetched from a source, not a single file, so almost
 * none of the book columns apply to it.
 */
@Entity(
    tableName = "manga",
    indices = [
        Index(value = ["sourceId", "url"], unique = true),
        Index("favorite"),
        Index("title"),
    ],
)
data class MangaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The source this came from, or [LOCAL_SOURCE_ID] for manga imported from files. */
    val sourceId: Long,
    /** Source URL, without scheme and domain. Unique per source. */
    val url: String,
    val title: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    /** Comma-separated, as the source gives it. */
    val genre: String? = null,
    /** One of the `SManga` status constants. */
    val status: Int = 0,
    /** Remote cover URL. */
    val thumbnailUrl: String? = null,
    /** Cached cover file in app storage. */
    val coverPath: String? = null,
    /** In the library, as opposed to only seen while browsing. */
    val favorite: Boolean = true,
    /** Details have been fetched at least once. */
    val initialized: Boolean = false,
    /** The source said to fetch chapters only once; library updates skip it. */
    val onlyFetchOnce: Boolean = false,
    val dateAdded: Long,
    val lastChapterFetchAt: Long? = null,
    val lastReadAt: Long? = null,
    /** The source's own notes on this manga (`SManga.memo`), as JSON; null when it kept none. */
    val memo: String? = null,
) {
    companion object {
        /** Manga imported from the device rather than fetched from an extension. */
        const val LOCAL_SOURCE_ID = 0L
    }
}

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
    indices = [
        Index(value = ["mangaId", "url"], unique = true),
        Index("read"),
    ],
)
data class MangaChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val chapterNumber: Float = -1f,
    /** Upload time as the source reports it; 0 when unknown. */
    val dateUpload: Long = 0,
    /** When this app first saw the chapter. */
    val dateFetch: Long,
    /** Position in the source's own chapter list, newest first. */
    val sourceOrder: Int = 0,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val pageCount: Int? = null,
    /** The source's own notes on this chapter (`SChapter.memo`), as JSON; null when it kept none. */
    val memo: String? = null,
)

/**
 * The download queue and the record of what is on disk. Disk stays the source of truth: a row
 * saying [MangaDownloadStatus.DOWNLOADED] is only trusted while its [path] still exists.
 */
@Entity(
    tableName = "manga_downloads",
    foreignKeys = [
        ForeignKey(
            entity = MangaChapterEntity::class,
            parentColumns = ["id"],
            childColumns = ["chapterId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MangaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mangaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["chapterId"], unique = true),
        Index("mangaId"),
        Index("status"),
    ],
)
data class MangaDownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chapterId: Long,
    val mangaId: Long,
    val status: MangaDownloadStatus = MangaDownloadStatus.QUEUED,
    /** The finished CBZ, or the directory while pages are still being written. */
    val path: String? = null,
    val pageCount: Int? = null,
    val downloadedPages: Int = 0,
    val sizeBytes: Long = 0,
    val queuedAt: Long,
    val completedAt: Long? = null,
    val error: String? = null,
)

/**
 * An extension repository the user added. Folio ships with none; every entry here was entered
 * by the user.
 */
@Entity(tableName = "extension_repos")
data class ExtensionRepoEntity(
    @PrimaryKey val baseUrl: String,
    val name: String,
    val shortName: String? = null,
    val website: String,
    /** SHA-256 of the signing certificate every extension from this repo must carry. */
    val signingKeyFingerprint: String,
    val addedAt: Long,
)

/**
 * Signing keys the user accepted for an extension. An extension is loaded only when the exact
 * package, version and signature were trusted, so a replacement APK has to be accepted again.
 */
@Entity(tableName = "trusted_extensions", primaryKeys = ["pkgName", "versionCode", "signature"])
data class TrustedExtensionEntity(
    val pkgName: String,
    val versionCode: Long,
    val signature: String,
    val trustedAt: Long,
)
