package app.folio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MangaDao {

    @Query("SELECT * FROM manga WHERE favorite = 1 ORDER BY title")
    fun observeLibrary(): Flow<List<MangaEntity>>

    @Query("SELECT * FROM manga WHERE id = :id")
    fun observe(id: Long): Flow<MangaEntity?>

    @Query("SELECT * FROM manga WHERE id = :id")
    suspend fun get(id: Long): MangaEntity?

    @Query("SELECT * FROM manga WHERE sourceId = :sourceId AND url = :url LIMIT 1")
    suspend fun find(sourceId: Long, url: String): MangaEntity?

    @Insert
    suspend fun insert(manga: MangaEntity): Long

    @Update
    suspend fun update(manga: MangaEntity)

    @Query("UPDATE manga SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** Browsed-only manga (never favorited) with no downloaded or read chapters can be pruned. */
    @Query(
        """DELETE FROM manga WHERE favorite = 0 AND id NOT IN (
               SELECT DISTINCT mangaId FROM manga_chapters WHERE read = 1 OR bookmark = 1
           ) AND id NOT IN (SELECT DISTINCT mangaId FROM manga_downloads)""",
    )
    suspend fun deleteBrowsedOnly(): Int

    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId ORDER BY sourceOrder")
    fun observeChapters(mangaId: Long): Flow<List<MangaChapterEntity>>

    @Query("SELECT * FROM manga_chapters WHERE mangaId = :mangaId ORDER BY sourceOrder")
    suspend fun chapters(mangaId: Long): List<MangaChapterEntity>

    @Query("SELECT * FROM manga_chapters WHERE id = :id")
    suspend fun chapter(id: Long): MangaChapterEntity?

    @Insert
    suspend fun insertChapters(chapters: List<MangaChapterEntity>): List<Long>

    @Update
    suspend fun updateChapters(chapters: List<MangaChapterEntity>)

    @Query("DELETE FROM manga_chapters WHERE id IN (:ids)")
    suspend fun deleteChapters(ids: List<Long>)

    @Query("UPDATE manga_chapters SET read = :read WHERE id IN (:ids)")
    suspend fun setRead(ids: List<Long>, read: Boolean)

    @Query("UPDATE manga_chapters SET bookmark = :bookmark WHERE id IN (:ids)")
    suspend fun setBookmark(ids: List<Long>, bookmark: Boolean)

    @Query("UPDATE manga_chapters SET lastPageRead = :page, pagesCount = :pages WHERE id = :id")
    suspend fun setProgress(id: Long, page: Int, pages: Int)

    @Transaction
    suspend fun replaceChapters(mangaId: Long, toInsert: List<MangaChapterEntity>, toUpdate: List<MangaChapterEntity>, toDelete: List<Long>) {
        if (toDelete.isNotEmpty()) deleteChapters(toDelete)
        if (toUpdate.isNotEmpty()) updateChapters(toUpdate)
        if (toInsert.isNotEmpty()) insertChapters(toInsert)
    }

    @Query("SELECT * FROM manga_downloads")
    fun observeDownloadQueue(): Flow<List<MangaDownloadEntity>>

    @Query("SELECT * FROM manga_downloads WHERE chapterId = :chapterId LIMIT 1")
    suspend fun download(chapterId: Long): MangaDownloadEntity?

    @Insert
    suspend fun enqueueDownload(download: MangaDownloadEntity): Long

    @Query("DELETE FROM manga_downloads WHERE chapterId IN (:chapterIds)")
    suspend fun dequeue(chapterIds: List<Long>)

    @Query("SELECT * FROM extension_repos ORDER BY name")
    fun observeRepos(): Flow<List<ExtensionRepoEntity>>

    @Query("SELECT * FROM extension_repos")
    suspend fun repos(): List<ExtensionRepoEntity>

    @Insert
    suspend fun insertRepo(repo: ExtensionRepoEntity): Long

    @Query("DELETE FROM extension_repos WHERE id = :id")
    suspend fun deleteRepo(id: Long)

    @Query("SELECT * FROM trusted_extensions WHERE packageName = :packageName")
    suspend fun trustFor(packageName: String): List<TrustedExtensionEntity>

    @Insert
    suspend fun trust(entry: TrustedExtensionEntity): Long

    @Query("DELETE FROM trusted_extensions WHERE packageName = :packageName")
    suspend fun untrust(packageName: String)
}
