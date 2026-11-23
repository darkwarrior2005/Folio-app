package app.folio.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.folio.core.model.MangaDownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * A library row: the manga plus the counts the library grid shows on it.
 */
data class MangaWithCounts(
    @Embedded val manga: MangaEntity,
    val chapterCount: Int,
    val unreadCount: Int,
    val downloadedCount: Int,
)

@Dao
interface MangaDao {

    // Manga

    @Query(
        """
        SELECT m.*,
               (SELECT COUNT(*) FROM manga_chapters c WHERE c.mangaId = m.id) AS chapterCount,
               (SELECT COUNT(*) FROM manga_chapters c WHERE c.mangaId = m.id AND c.read = 0) AS unreadCount,
               (SELECT COUNT(*) FROM manga_downloads d
                  WHERE d.mangaId = m.id AND d.status = 'DOWNLOADED') AS downloadedCount
        FROM manga m
        WHERE m.favorite = 1
        """,
    )
    fun observeLibrary(): Flow<List<MangaWithCounts>>

    @Query("SELECT * FROM manga WHERE id = :id")
    fun observe(id: Long): Flow<MangaEntity?>

    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun get(id: Long): MangaEntity?

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId AND url = :url LIMIT 1")
    suspend fun find(sourceId: Long, url: String): MangaEntity?

    @Query("SELECT * FROM manga WHERE favorite = 1 AND onlyFetchOnce = 0")
    suspend fun favorites(): List<MangaEntity>

    @Insert
    suspend fun insert(manga: MangaEntity): Long

    @Update
    suspend fun update(manga: MangaEntity)

    @Query("UPDATE manga SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE manga SET lastReadAt = :now WHERE id = :id")
    suspend fun touchRead(id: Long, now: Long)

    @Query("DELETE FROM manga WHERE id = :id")
    suspend fun delete(id: Long)

    /** Manga that were only browsed, never added, and have nothing downloaded. */
    @Query(
        """
        DELETE FROM manga
        WHERE favorite = 0
          AND id NOT IN (SELECT mangaId FROM manga_downloads)
        """,
    )
    suspend fun deleteOrphans(): Int

    // Chapters

    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId ORDER BY sourceOrder ASC")
    fun observeChapters(mangaId: Long): Flow<List<MangaChapterEntity>>

    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId ORDER BY sourceOrder ASC")
    suspend fun chapters(mangaId: Long): List<MangaChapterEntity>

    @Query("SELECT * FROM manga_chapters WHERE id = :id")
    suspend fun chapter(id: Long): MangaChapterEntity?

    @Query("SELECT * FROM manga_chapters WHERE id IN (:ids)")
    suspend fun chaptersByIds(ids: List<Long>): List<MangaChapterEntity>

    @Insert
    suspend fun insertChapters(chapters: List<MangaChapterEntity>): List<Long>

    @Update
    suspend fun updateChapters(chapters: List<MangaChapterEntity>)

    @Query("DELETE FROM manga_chapters WHERE id IN (:ids)")
    suspend fun deleteChapters(ids: List<Long>)

    @Query("UPDATE manga_chapters SET read = :read, lastPageRead = :lastPageRead WHERE id = :id")
    suspend fun setChapterRead(id: Long, read: Boolean, lastPageRead: Int)

    @Query("UPDATE manga_chapters SET read = :read WHERE id IN (:ids)")
    suspend fun setChaptersRead(ids: List<Long>, read: Boolean)

    @Query("UPDATE manga_chapters SET bookmark = :bookmark WHERE id = :id")
    suspend fun setChapterBookmark(id: Long, bookmark: Boolean)

    // Downloads

    @Query("SELECT * FROM manga_downloads WHERE status IN ('QUEUED', 'DOWNLOADING') ORDER BY queuedAt ASC")
    fun observeQueue(): Flow<List<MangaDownloadEntity>>

    @Query("SELECT * FROM manga_downloads WHERE status IN ('QUEUED', 'DOWNLOADING') ORDER BY queuedAt ASC")
    suspend fun queue(): List<MangaDownloadEntity>

    @Query("SELECT * FROM manga_downloads WHERE mangaId = :mangaId")
    fun observeDownloadsFor(mangaId: Long): Flow<List<MangaDownloadEntity>>

    @Query("SELECT * FROM manga_downloads WHERE mangaId = :mangaId")
    suspend fun downloadsForManga(mangaId: Long): List<MangaDownloadEntity>

    @Query("SELECT * FROM manga_downloads WHERE chapterId = :chapterId LIMIT 1")
    suspend fun download(chapterId: Long): MangaDownloadEntity?

    @Query("SELECT * FROM manga_downloads WHERE chapterId IN (:chapterIds)")
    suspend fun downloads(chapterIds: List<Long>): List<MangaDownloadEntity>

    @Query("SELECT * FROM manga_downloads WHERE status = 'DOWNLOADED'")
    suspend fun completedDownloads(): List<MangaDownloadEntity>

    /** Queueing the same chapter twice is a no-op rather than an error. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(download: MangaDownloadEntity): Long

    @Update
    suspend fun updateDownload(download: MangaDownloadEntity)

    @Query("DELETE FROM manga_downloads WHERE chapterId IN (:chapterIds)")
    suspend fun deleteDownloads(chapterIds: List<Long>)

    @Query("DELETE FROM manga_downloads WHERE mangaId = :mangaId")
    suspend fun deleteDownloadsFor(mangaId: Long)

    @Query("DELETE FROM manga_downloads WHERE status IN ('QUEUED', 'DOWNLOADING')")
    suspend fun clearQueue()

    /** On startup, anything left mid-download is back in the queue. */
    @Query("UPDATE manga_downloads SET status = 'QUEUED' WHERE status = 'DOWNLOADING'")
    suspend fun requeueInterrupted()

    @Query("SELECT COUNT(*) FROM manga_downloads WHERE status IN ('QUEUED', 'DOWNLOADING')")
    fun observeQueueSize(): Flow<Int>

    @Transaction
    suspend fun markDownloaded(chapterId: Long, path: String, pages: Int, sizeBytes: Long, now: Long) {
        val existing = download(chapterId) ?: return
        updateDownload(
            existing.copy(
                status = MangaDownloadStatus.DOWNLOADED,
                path = path,
                pageCount = pages,
                downloadedPages = pages,
                sizeBytes = sizeBytes,
                completedAt = now,
                error = null,
            ),
        )
    }

    // Extension repositories and trust

    @Query("SELECT * FROM extension_repos ORDER BY name ASC")
    fun observeRepos(): Flow<List<ExtensionRepoEntity>>

    @Query("SELECT * FROM extension_repos ORDER BY name ASC")
    suspend fun repos(): List<ExtensionRepoEntity>

    @Upsert
    suspend fun upsertRepo(repo: ExtensionRepoEntity)

    @Query("DELETE FROM extension_repos WHERE baseUrl = :baseUrl")
    suspend fun deleteRepo(baseUrl: String)

    @Query("SELECT * FROM trusted_extensions")
    suspend fun trusted(): List<TrustedExtensionEntity>

    @Query(
        """
        SELECT COUNT(*) > 0 FROM trusted_extensions
        WHERE pkgName = :pkgName AND versionCode = :versionCode AND signature = :signature
        """,
    )
    suspend fun isTrusted(pkgName: String, versionCode: Long, signature: String): Boolean

    @Upsert
    suspend fun trust(entry: TrustedExtensionEntity)

    @Query("DELETE FROM trusted_extensions WHERE pkgName = :pkgName")
    suspend fun revokeTrust(pkgName: String)

    @Query("DELETE FROM trusted_extensions")
    suspend fun revokeAllTrust()
}
