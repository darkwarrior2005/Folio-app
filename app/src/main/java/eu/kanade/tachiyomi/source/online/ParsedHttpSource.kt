package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A source for a website parsed with Jsoup, driven by CSS selectors.
 */
@Suppress("DEPRECATION")
@Deprecated(
    message = "In most cases sources only require a subset of the methods from this class. " +
        "Source developers should make their own implementation according to their needs.",
)
abstract class ParsedHttpSource : HttpSource() {

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        val hasNextPage = popularMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    /** Selector matching one element per manga. */
    protected abstract fun popularMangaSelector(): String

    /** Builds a manga from an element matched by [popularMangaSelector]. */
    protected abstract fun popularMangaFromElement(element: Element): SManga

    /** Selector for the link to the next page, or null when there is none. */
    protected abstract fun popularMangaNextPageSelector(): String?

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    protected abstract fun searchMangaSelector(): String

    protected abstract fun searchMangaFromElement(element: Element): SManga

    protected abstract fun searchMangaNextPageSelector(): String?

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    protected abstract fun latestUpdatesSelector(): String

    protected abstract fun latestUpdatesFromElement(element: Element): SManga

    protected abstract fun latestUpdatesNextPageSelector(): String?

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    protected abstract fun mangaDetailsParse(document: Document): SManga

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    protected abstract fun chapterListSelector(): String

    protected abstract fun chapterFromElement(element: Element): SChapter

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    protected abstract fun pageListParse(document: Document): List<Page>

    override fun imageUrlParse(response: Response): String = imageUrlParse(response.asJsoup())

    protected abstract fun imageUrlParse(document: Document): String
}
