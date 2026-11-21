package app.folio.data.repo

import app.folio.core.model.Manga
import app.folio.core.model.MangaChapter
import app.folio.core.model.MangaChapterSync
import app.folio.data.db.FolioDatabase
import app.folio.data.db.MangaEntity
import app.folio.manga.applySManga
import app.folio.manga.toDomain
import app.folio.manga.toEntity
import app.folio.manga.toNewEntity
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MangaRepository(private val db: FolioDatabase) {

    fun observeLibrary(): Flow<List<Manga>> =
        db.manga().observeLibrary().map { entities -> entities.map { it.toDomain() } }

    fun observe(mangaId: Long): Flow<Manga?> =
        db.manga().observe(mangaId).map { it?.toDomain() }

    fun observeChapters(mangaId: Long): Flow<List<MangaChapter>> =
        db.manga().observeChapters(mangaId).map { entities -> entities.map { it.toDomain() } }

    /**
     * Returns the manga for (sourceId, url), inserting it if it's new. A manga already in the
     * library keeps its stored title/cover; a merely-browsed one is refreshed from [source].
     */
    suspend fun findOrCreate(sourceId: Long, source: SManga): Manga = withContext(Dispatchers.IO) {
        val dao = db.manga()
        val existing = dao.find(sourceId, source.url)
        val entity = if (existing == null) {
            val created = source.toNewEntity(sourceId, System.currentTimeMillis())
            val id = dao.insert(created)
            created.copy(id = id)
        } else if (!existing.favorite) {
            val refreshed = existing.applySManga(source)
            dao.update(refreshed)
            refreshed
        } else {
            existing
        }
        entity.toDomain()
    }

    suspend fun setFavorite(mangaId: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        db.manga().setFavorite(mangaId, favorite)
    }

    /**
     * Reconciles [fetched] against what's stored for [mangaId], following [MangaChapterSync]'s
     * rules, and applies the result. Throws NoChaptersException without touching the database
     * when [fetched] is empty - an empty refresh never wipes existing chapters.
     */
    suspend fun syncChapters(mangaId: Long, mangaTitle: String, fetched: List<SChapter>): Unit =
        withContext(Dispatchers.IO) {
            val existing = db.manga().chapters(mangaId).map { it.toDomain() }
            val now = System.currentTimeMillis()
            val result = MangaChapterSync.sync(mangaTitle, existing, fetched, now)
            db.manga().replaceChapters(
                mangaId = mangaId,
                toInsert = result.toInsert.map { it.copy(mangaId = mangaId).toEntity() },
                toUpdate = result.toUpdate.map { it.copy(mangaId = mangaId).toEntity() },
                toDelete = result.toDeleteIds,
            )
            if (fetched.isNotEmpty()) {
                db.manga().get(mangaId)?.let { manga ->
                    db.manga().update(manga.copy(lastUpdate = now))
                }
            }
        }

    suspend fun setChaptersRead(ids: List<Long>, read: Boolean) = withContext(Dispatchers.IO) {
        db.manga().setRead(ids, read)
    }

    suspend fun setChaptersBookmarked(ids: List<Long>, bookmark: Boolean) = withContext(Dispatchers.IO) {
        db.manga().setBookmark(ids, bookmark)
    }

    suspend fun setProgress(chapterId: Long, page: Int, pages: Int) = withContext(Dispatchers.IO) {
        db.manga().setProgress(chapterId, page, pages)
    }

    /** Drops browsed-but-never-favorited manga with nothing downloaded or read, e.g. on "forget browsing". */
    suspend fun deleteBrowsedOnly() = withContext(Dispatchers.IO) {
        db.manga().deleteBrowsedOnly()
    }
}
