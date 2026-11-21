package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.awaitSingle
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * The base class most Keiyoushi extensions extend. Its non-suspend `*Request`/`*Parse` methods
 * are the extension's actual override surface - keep those method names and signatures exact.
 */
abstract class HttpSource : CatalogueSource {

    private val network: NetworkHelper by injectLazy()

    /** The source's own OkHttp client. Overridden sources may add interceptors (rate limits). */
    open val client: OkHttpClient
        get() = network.client

    /** Base site URL, with no trailing slash. */
    abstract val baseUrl: String

    override val id: Long by lazy { generateId(name, lang, versionId) }

    open val versionId: Int = 1

    open fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", network.defaultUserAgentProvider())

    open val headers: Headers by lazy { headersBuilder().build() }

    // ---- Popular ----
    abstract fun popularMangaRequest(page: Int): Request
    abstract fun popularMangaParse(response: Response): MangasPage

    override fun fetchPopularManga(page: Int): Observable<MangasPage> =
        client.newCall(popularMangaRequest(page)).asObservableSuccess()
            .map { popularMangaParse(it) }

    // ---- Search ----
    abstract fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request
    abstract fun searchMangaParse(response: Response): MangasPage

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> =
        client.newCall(searchMangaRequest(page, query, filters)).asObservableSuccess()
            .map { searchMangaParse(it) }

    // ---- Latest ----
    open fun latestUpdatesRequest(page: Int): Request =
        throw UnsupportedOperationException("This source does not support latest")
    open fun latestUpdatesParse(response: Response): MangasPage =
        throw UnsupportedOperationException("This source does not support latest")

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> =
        client.newCall(latestUpdatesRequest(page)).asObservableSuccess()
            .map { latestUpdatesParse(it) }

    // ---- Details ----
    abstract fun mangaDetailsRequest(manga: SManga): Request
    abstract fun mangaDetailsParse(response: Response): SManga

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        client.newCall(mangaDetailsRequest(manga)).asObservableSuccess()
            .map { mangaDetailsParse(it).apply { initialized = true } }

    open suspend fun getMangaUrl(manga: SManga): String = mangaDetailsRequest(manga).url.toString()

    // ---- Chapters ----
    abstract fun chapterListRequest(manga: SManga): Request
    abstract fun chapterListParse(response: Response): List<SChapter>

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        client.newCall(chapterListRequest(manga)).asObservableSuccess()
            .map { chapterListParse(it) }

    open suspend fun getChapterUrl(chapter: SChapter): String = chapter.url

    // ---- Pages ----
    abstract fun pageListRequest(chapter: SChapter): Request
    abstract fun pageListParse(response: Response): List<Page>

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> =
        client.newCall(pageListRequest(chapter)).asObservableSuccess()
            .map { pageListParse(it) }

    open fun imageUrlRequest(page: Page): Request = GET(page.imageUrl!!, headers)
    open fun imageUrlParse(response: Response): String =
        throw UnsupportedOperationException("Not used")

    open fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    suspend fun fetchImage(page: Page): Response =
        client.newCall(imageRequest(page)).awaitSuccess()

    /** Coroutine-friendly wrappers that newer extensions and Folio's own code call. */
    open suspend fun getPopularManga(page: Int): MangasPage = fetchPopularManga(page).awaitSingle()
    open suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        fetchSearchManga(page, query, filters).awaitSingle()
    open suspend fun getLatestUpdates(page: Int): MangasPage = fetchLatestUpdates(page).awaitSingle()
    open suspend fun getMangaDetails(manga: SManga): SManga = fetchMangaDetails(manga).awaitSingle()
    open suspend fun getChapterList(manga: SManga): List<SChapter> = fetchChapterList(manga).awaitSingle()
    open suspend fun getPageList(chapter: SChapter): List<Page> = fetchPageList(chapter).awaitSingle()

    override fun toString() = "$name (${lang.uppercase()})"

    override fun equals(other: Any?) = other is HttpSource && id == other.id
    override fun hashCode() = id.hashCode()

    companion object {
        fun generateId(name: String, lang: String, versionId: Int): Long {
            val key = "${name.lowercase()}/$lang/$versionId"
            val bytes = java.security.MessageDigest.getInstance("MD5").digest(key.toByteArray())
            return (0..7).fold(0L) { acc, i -> acc shl 8 or (bytes[i].toLong() and 0xff) } and Long.MAX_VALUE
        }
    }
}
