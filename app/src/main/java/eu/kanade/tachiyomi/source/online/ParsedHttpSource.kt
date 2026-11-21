package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

/** For sites with no JSON API: every `*Parse` is driven by CSS selectors over the raw HTML. */
abstract class ParsedHttpSource : HttpSource() {

    abstract fun popularMangaSelector(): String
    abstract fun popularMangaFromElement(element: Element): SManga
    abstract fun popularMangaNextPageSelector(): String?

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector()?.let { document.select(it).first() != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    abstract fun searchMangaSelector(): String
    abstract fun searchMangaFromElement(element: Element): SManga
    abstract fun searchMangaNextPageSelector(): String?

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = searchMangaNextPageSelector()?.let { document.select(it).first() != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    open fun latestUpdatesSelector(): String =
        throw UnsupportedOperationException("This source does not support latest")
    open fun latestUpdatesFromElement(element: Element): SManga =
        throw UnsupportedOperationException("This source does not support latest")
    open fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.select(it).first() != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    abstract fun mangaDetailsParse(document: org.jsoup.nodes.Document): SManga
    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    open fun chapterListSelector(): String = throw UnsupportedOperationException("Stub")
    abstract fun chapterFromElement(element: Element): SChapter

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    abstract fun pageListParse(document: org.jsoup.nodes.Document): List<Page>
    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    override fun imageUrlParse(response: Response): String = imageUrlParse(response.asJsoup())
    open fun imageUrlParse(document: org.jsoup.nodes.Document): String =
        throw UnsupportedOperationException("Not used")
}
