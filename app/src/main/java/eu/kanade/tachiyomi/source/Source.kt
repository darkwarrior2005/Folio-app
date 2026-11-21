package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import rx.Observable

interface Source {

    val id: Long

    val name: String

    @Suppress("DEPRECATION")
    fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        throw IllegalStateException("Not used")

    @Suppress("DEPRECATION")
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        throw IllegalStateException("Not used")

    @Suppress("DEPRECATION")
    fun fetchPageList(chapter: SChapter): Observable<List<eu.kanade.tachiyomi.source.model.Page>> =
        throw IllegalStateException("Not used")
}
