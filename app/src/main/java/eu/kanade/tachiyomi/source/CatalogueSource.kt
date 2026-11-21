package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import rx.Observable

interface CatalogueSource : Source {

    /** Languages this source's entries are in, such as "en" for only English manga. */
    val lang: String

    /** Whether this source supports latest listing. */
    val supportsLatest: Boolean

    @Suppress("DEPRECATION")
    fun fetchPopularManga(page: Int): Observable<MangasPage> =
        throw IllegalStateException("Not used")

    @Suppress("DEPRECATION")
    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        throw IllegalStateException("Not used")

    @Suppress("DEPRECATION")
    fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        throw IllegalStateException("Not used")

    fun getFilterList(): FilterList = FilterList()
}
