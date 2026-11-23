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
import eu.kanade.tachiyomi.util.lang.awaitSingle
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

/**
 * Base class for a source backed by a website.
 */
abstract class HttpSource : CatalogueSource {

    protected val network: NetworkHelper by injectLazy()

    /**
     * Base url of the website without a trailing slash, like `https://mysite.com`.
     */
    abstract val baseUrl: String

    /**
     * The page "Open in browser" opens. Defaults to [baseUrl].
     */
    open fun getHomeUrl(): String = baseUrl

    /**
     * Raise this when a site changes so much that its old URLs no longer work; the source then
     * gets a new [id] and is treated as a different source.
     */
    open val versionId: Int = 1

    /**
     * Generated from the name, language and [versionId]. See [generateId].
     */
    override val id: Long by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient get() = network.client

    /**
     * The first 16 hex characters (64 bits) of the MD5 of `"${name.lowercase()}/$lang/$versionId"`,
     * with the sign bit cleared. Can be called directly to reproduce an older ID when a source is
     * renamed and a migration is not wanted.
     *
     * @since extensions-lib 1.5
     */
    @Suppress("MemberVisibilityCanBePrivate")
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        add("User-Agent", network.defaultUserAgentProvider())
    }

    override fun toString(): String = "$name (${lang.uppercase()})"

    @Suppress("DEPRECATION")
    @Deprecated("Use the suspend API instead", ReplaceWith("getPopularManga"))
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response -> popularMangaParse(response) }
    }

    @Deprecated(HELPER_DEPRECATION)
    protected open fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException()

    @Deprecated(HELPER_DEPRECATION)
    protected open fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    @Deprecated("Use the suspend API instead", ReplaceWith("getSearchManga"))
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response -> searchMangaParse(response) }
    }

    @Deprecated(HELPER_DEPRECATION)
    protected open fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request = throw UnsupportedOperationException()

    @Deprecated(HELPER_DEPRECATION)
    protected open fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    @Deprecated("Use the suspend API instead", ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response -> latestUpdatesParse(response) }
    }

    @Deprecated(HELPER_DEPRECATION)
    protected open fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    @Deprecated(HELPER_DEPRECATION)
    protected open fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    @Deprecated("Use the combined suspend API instead", replaceWith = ReplaceWith("getMangaUpdate"))
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response -> mangaDetailsParse(response).apply { initialized = true } }
    }

    @Deprecated(HELPER_DEPRECATION)
    open fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    @Deprecated(HELPER_DEPRECATION)
    protected open fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    @Deprecated("Use the combined suspend API instead", replaceWith = ReplaceWith("getMangaUpdate"))
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response -> chapterListParse(response) }
    }

    @Deprecated(HELPER_DEPRECATION)
    protected open fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    @Deprecated(HELPER_DEPRECATION)
    protected open fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    @Deprecated("Use the suspend API instead", ReplaceWith("getPageList"))
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response -> pageListParse(response) }
    }

    @Deprecated(HELPER_DEPRECATION)
    protected open fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    @Deprecated(HELPER_DEPRECATION)
    protected open fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    @Suppress("DEPRECATION")
    @Deprecated("Use the suspend API instead", ReplaceWith("getImageUrl"))
    open fun fetchImageUrl(page: Page): Observable<String> {
        return client.newCall(imageUrlRequest(page))
            .asObservableSuccess()
            .map { imageUrlParse(it) }
    }

    /**
     * Resolves the image URL of [page]. Only called when [Page.imageUrl] is null.
     *
     * @since tachiyomix 1.6
     */
    @Suppress("DEPRECATION")
    open suspend fun getImageUrl(page: Page): String = fetchImageUrl(page).awaitSingle()

    @Deprecated(HELPER_DEPRECATION)
    protected open fun imageUrlRequest(page: Page): Request = GET(page.url, headers)

    @Deprecated(HELPER_DEPRECATION)
    protected open fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    suspend fun getImage(page: Page, existingSize: Long = 0L): Response {
        return client.newCachelessCallWithProgress(imageRequest(page), page, existingSize)
            .awaitSuccess()
    }

    protected open fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headers)

    /**
     * Stores the chapter URL without scheme and domain, so saved URLs survive a domain change.
     */
    @Suppress("Unused")
    fun SChapter.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    /**
     * Stores the manga URL without scheme and domain, so saved URLs survive a domain change.
     */
    @Suppress("Unused")
    fun SManga.setUrlWithoutDomain(url: String) {
        this.url = getUrlWithoutDomain(url)
    }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig.replace(" ", "%20"))
            var out = uri.path
            if (uri.query != null) {
                out += "?" + uri.query
            }
            if (uri.fragment != null) {
                out += "#" + uri.fragment
            }
            out
        } catch (_: URISyntaxException) {
            orig
        }
    }

    /**
     * @since extensions-lib 1.4
     */
    @Suppress("DEPRECATION")
    open fun getMangaUrl(manga: SManga): String = mangaDetailsRequest(manga).url.toString()

    /**
     * @since extensions-lib 1.4
     */
    @Suppress("DEPRECATION")
    open fun getChapterUrl(chapter: SChapter): String = pageListRequest(chapter).url.toString()

    @Deprecated("All modifications should be done when constructing the chapter")
    open fun prepareNewChapter(chapter: SChapter, manga: SManga) {}

    private companion object {
        const val HELPER_DEPRECATION = "The helper functions are inherently limiting and hide the " +
            "underlying implementation. Source developers should make their own implementation " +
            "according to their needs."
    }
}
