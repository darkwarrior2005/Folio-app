package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import rx.Observable

/**
 * A place manga can be read from: an online catalogue, the device's own files, and so on.
 *
 * Ported from Mihon (Apache-2.0). The package and member names are kept so extensions built
 * against the Tachiyomi/Mihon extensions-lib load unchanged.
 */
interface Source {

    /**
     * Unique ID for the source.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Whether the source can list latest updates.
     */
    val supportsLatest: Boolean

    /**
     * The filters this source offers.
     */
    fun getFilterList(): FilterList = FilterList()

    /**
     * @since tachiyomix 1.6
     */
    suspend fun getPopularManga(page: Int): MangasPage

    /**
     * @since tachiyomix 1.6
     */
    suspend fun getLatestUpdates(page: Int): MangasPage

    /**
     * @since tachiyomix 1.6
     */
    suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage

    /**
     * Fetches updated details, chapters, or both for a manga. A value that was not asked for can
     * be returned unchanged.
     *
     * @since tachiyomix 1.6
     */
    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate

    /**
     * The pages of a chapter, in reading order. The index is ignored.
     *
     * @since tachiyomix 1.6
     */
    suspend fun getPageList(chapter: SChapter): List<Page>

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getMangaUpdate"))
    fun fetchMangaDetails(manga: SManga): Observable<SManga> = throw UnsupportedOperationException()

    @Deprecated("Use the combined suspend API instead", ReplaceWith("getMangaUpdate"))
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = throw UnsupportedOperationException()

    @Deprecated("Use the suspend API instead", ReplaceWith("getPageList"))
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> = throw UnsupportedOperationException()
}
