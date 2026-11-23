package app.folio.data.repo

import androidx.room.withTransaction
import app.folio.core.model.ExistingChapter
import app.folio.core.model.MangaChapterSync
import app.folio.core.model.NoChaptersException
import app.folio.core.model.SourceChapter
import app.folio.data.db.FolioDatabase
import app.folio.data.db.MangaChapterEntity
import app.folio.data.db.MangaEntity
import app.folio.data.db.MangaWithCounts
import app.folio.manga.memoText
import app.folio.manga.toSManga
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow

/** What a chapter sync changed, for the "N new chapters" message. */
data class ChapterSyncResult(val added: Int, val updated: Int, val removed: Int)

/**
 * The manga library: series, their chapters and read state. Downloads are bookkept here too,
 * but the files themselves belong to the download manager.
 */
class MangaRepository(private val database: FolioDatabase) {

    private val dao get() = database.manga()

    fun observeLibrary(): Flow<List<MangaWithCounts>> = dao.observeLibrary()

    fun observe(id: Long): Flow<MangaEntity?> = dao.observe(id)

    fun observeChapters(mangaId: Long): Flow<List<MangaChapterEntity>> = dao.observeChapters(mangaId)

    suspend fun get(id: Long): MangaEntity? = dao.get(id)

    suspend fun chapter(id: Long): MangaChapterEntity? = dao.chapter(id)

    suspend fun chapters(mangaId: Long): List<MangaChapterEntity> = dao.chapters(mangaId)

    /**
     * Finds the stored manga for a source result, inserting it if this is the first time it has
     * been seen. Browsing stores it with [MangaEntity.favorite] false so the details and chapter
     * list can be cached without it appearing in the library.
     */
    suspend fun findOrCreate(sourceId: Long, manga: SManga, favorite: Boolean = false): MangaEntity {
        val existing = dao.find(sourceId, manga.url)
        if (existing != null) {
            // As Mihon does: a manga only browsed past takes the title and cover the source shows
            // now; one in the library keeps its own, and only gains a cover it never had.
            val title = runCatching { manga.title }.getOrDefault("").ifBlank { existing.title }
            val refreshed = if (existing.favorite) {
                existing.copy(thumbnailUrl = existing.thumbnailUrl ?: manga.thumbnail_url)
            } else {
                existing.copy(title = title, thumbnailUrl = manga.thumbnail_url ?: existing.thumbnailUrl)
            }
            if (refreshed != existing) dao.update(refreshed)
            return refreshed
        }

        val now = System.currentTimeMillis()
        val entity = MangaEntity(
            sourceId = sourceId,
            url = manga.url,
            title = manga.title,
            author = manga.author,
            artist = manga.artist,
            description = manga.description,
            genre = manga.genre,
            status = manga.status,
            thumbnailUrl = manga.thumbnail_url,
            favorite = favorite,
            initialized = manga.initialized,
            onlyFetchOnce = manga.update_strategy == UpdateStrategy.ONLY_FETCH_ONCE,
            dateAdded = now,
            memo = memoText { manga.memo },
        )
        val id = dao.insert(entity)
        return entity.copy(id = id)
    }

    /**
     * Applies fresh details from the source. The user's own cover stays, and a field the source
     * left empty does not wipe what is already stored.
     */
    suspend fun applyDetails(id: Long, details: SManga) {
        val current = dao.get(id) ?: return
        dao.update(
            current.copy(
                title = details.title.ifBlank { current.title },
                author = details.author ?: current.author,
                artist = details.artist ?: current.artist,
                description = details.description ?: current.description,
                genre = details.genre ?: current.genre,
                status = details.status,
                thumbnailUrl = details.thumbnail_url ?: current.thumbnailUrl,
                initialized = true,
                onlyFetchOnce = details.update_strategy == UpdateStrategy.ONLY_FETCH_ONCE,
                memo = memoText { details.memo } ?: current.memo,
            ),
        )
    }

    /**
     * Brings the stored chapter list in line with what the source lists now, keeping read state
     * for chapters that are still there.
     */
    suspend fun syncChapters(
        mangaId: Long,
        sourceChapters: List<SChapter>,
        source: Source? = null,
    ): ChapterSyncResult {
        // Mihon refuses an empty list too: a site's bad moment must not delete every chapter,
        // every download and every read mark.
        if (sourceChapters.isEmpty()) throw NoChaptersException(hadChapters = dao.chapters(mangaId).isNotEmpty())
        val manga = dao.get(mangaId)
        val mangaTitle = manga?.title.orEmpty()

        // The source's own last word on each new chapter (an extension hook Mihon calls).
        if (source is HttpSource && manga != null) {
            val sManga = manga.toSManga()
            @Suppress("DEPRECATION")
            sourceChapters.forEach { chapter -> runCatching { source.prepareNewChapter(chapter, sManga) } }
        }

        // A chapter a source left without a URL cannot be stored or opened; one left without a
        // name still can. Both are lateinit in the extension API, so unset ones throw on read.
        val raw = sourceChapters.mapIndexedNotNull { index, chapter ->
            val url = runCatching { chapter.url }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            SourceChapter(
                url = url,
                name = runCatching { chapter.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}",
                scanlator = chapter.scanlator,
                chapterNumber = chapter.chapter_number,
                dateUpload = chapter.date_upload,
                sourceOrder = index,
                memo = memoText { chapter.memo },
            )
        }
        val incoming = MangaChapterSync.prepare(mangaTitle, raw)

        return database.withTransaction {
            val stored = dao.chapters(mangaId)
            val diff = MangaChapterSync.diff(
                source = incoming,
                existing = stored.map {
                    ExistingChapter(
                        id = it.id,
                        url = it.url,
                        name = it.name,
                        scanlator = it.scanlator,
                        chapterNumber = it.chapterNumber,
                        dateUpload = it.dateUpload,
                        sourceOrder = it.sourceOrder,
                        memo = it.memo,
                        read = it.read,
                        bookmark = it.bookmark,
                    )
                },
                now = System.currentTimeMillis(),
            )

            val now = System.currentTimeMillis()

            if (diff.toInsert.isNotEmpty()) {
                dao.insertChapters(
                    diff.toInsert.map {
                        MangaChapterEntity(
                            mangaId = mangaId,
                            url = it.url,
                            name = it.name,
                            scanlator = it.scanlator,
                            chapterNumber = it.chapterNumber,
                            dateUpload = it.dateUpload,
                            dateFetch = now,
                            sourceOrder = it.sourceOrder,
                            memo = it.memo,
                            read = it.read,
                            bookmark = it.bookmark,
                        )
                    },
                )
            }

            if (diff.toUpdate.isNotEmpty()) {
                val byId = stored.associateBy { it.id }
                dao.updateChapters(
                    diff.toUpdate.mapNotNull { (id, incomingChapter) ->
                        val current = byId[id] ?: return@mapNotNull null
                        current.copy(
                            name = incomingChapter.name,
                            scanlator = incomingChapter.scanlator,
                            chapterNumber = incomingChapter.chapterNumber,
                            // Keep the recorded date when the source does not report one.
                            dateUpload = incomingChapter.dateUpload.takeIf { it != 0L } ?: current.dateUpload,
                            sourceOrder = incomingChapter.sourceOrder,
                            memo = incomingChapter.memo ?: current.memo,
                        )
                    },
                )
            }

            if (diff.toDelete.isNotEmpty()) {
                dao.deleteChapters(diff.toDelete)
            }

            dao.get(mangaId)?.let { dao.update(it.copy(lastChapterFetchAt = now)) }

            ChapterSyncResult(
                added = diff.toInsert.size,
                updated = diff.toUpdate.size,
                removed = diff.toDelete.size,
            )
        }
    }

    suspend fun setFavorite(id: Long, favorite: Boolean) {
        dao.setFavorite(id, favorite)
    }

    suspend fun setChapterRead(chapterId: Long, read: Boolean, lastPageRead: Int = 0) {
        dao.setChapterRead(chapterId, read, lastPageRead)
    }

    suspend fun setChaptersRead(chapterIds: List<Long>, read: Boolean) {
        dao.setChaptersRead(chapterIds, read)
    }

    suspend fun setChapterBookmark(chapterId: Long, bookmark: Boolean) {
        dao.setChapterBookmark(chapterId, bookmark)
    }

    /** Records progress inside a chapter, and marks it read once the last page is reached. */
    suspend fun saveProgress(chapterId: Long, page: Int, pageCount: Int) {
        val finished = pageCount > 0 && page >= pageCount - 1
        dao.setChapterRead(chapterId, finished, page)
        dao.chapter(chapterId)?.let { dao.touchRead(it.mangaId, System.currentTimeMillis()) }
    }

    /** Removes a manga from the library. Chapters and download rows cascade. */
    suspend fun remove(id: Long) {
        dao.delete(id)
    }

    /** Drops cached manga the user only browsed past and never downloaded. */
    suspend fun pruneBrowsed(): Int = dao.deleteOrphans()

    suspend fun favorites(): List<MangaEntity> = dao.favorites()
}
