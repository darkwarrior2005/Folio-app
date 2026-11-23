package app.folio.manga.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchTest {

    private class FakeSource(
        override val id: Long,
        override val name: String,
        private val answer: suspend (String) -> MangasPage,
    ) : CatalogueSource {
        override val lang: String = "en"
        override val supportsLatest: Boolean = false

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = answer(query)
    }

    private fun page(vararg titles: String) = MangasPage(
        mangas = titles.map { title -> SManga.create().also { it.title = title; it.url = "/$title" } },
        hasNextPage = false,
    )

    @Test
    fun `each source reports on its own, in source order`() = runTest {
        val search = GlobalSearch(perSourceTimeout = 5.seconds, dispatcher = StandardTestDispatcher(testScheduler))
        val sources = listOf(
            FakeSource(1, "Finds it") { query -> page("$query vol 1", "$query vol 2") },
            FakeSource(2, "Finds nothing") { page() },
            FakeSource(3, "Broken") { error("HTTP error 503") },
            FakeSource(4, "Too new") { throw NoSuchMethodError("KeiSource.getMangaUrl") },
            FakeSource(5, "Hangs") { awaitCancellation() },
        )

        val emissions = search.search(sources, "Berserk").toList()

        assertTrue(emissions.first().all { it.state == SourceSearchState.Loading })
        val last = emissions.last()
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), last.map { it.sourceId })
        assertEquals(
            listOf("Berserk vol 1", "Berserk vol 2"),
            (last[0].state as SourceSearchState.Done).manga.map { it.title },
        )
        assertTrue((last[1].state as SourceSearchState.Done).manga.isEmpty())
        assertEquals("HTTP error 503", (last[2].state as SourceSearchState.Failed).message)
        assertTrue((last[3].state as SourceSearchState.Failed).message.contains("KeiSource.getMangaUrl"))
        assertTrue(last[4].state is SourceSearchState.Failed)
    }

    @Test
    fun `no more than the allowed number of sources search at once`() = runTest {
        val running = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val search = GlobalSearch(maxConcurrent = 2, dispatcher = StandardTestDispatcher(testScheduler))
        val sources = (1L..6L).map { id ->
            FakeSource(id, "Source $id") {
                peak.accumulateAndGet(running.incrementAndGet(), ::maxOf)
                delay(100)
                running.decrementAndGet()
                page("Result $id")
            }
        }

        val last = search.search(sources, "anything").last()

        assertEquals(2, peak.get())
        assertTrue(last.all { it.state is SourceSearchState.Done })
    }

    @Test
    fun `searching no sources finishes straight away`() = runTest {
        val emissions = GlobalSearch(dispatcher = StandardTestDispatcher(testScheduler)).search(emptyList(), "x").toList()
        assertEquals(listOf(emptyList<SourceSearchResult>()), emissions)
    }

    @Test
    fun `failures the website can fix offer to open it`() {
        assertTrue(IllegalStateException("Open in WebView to set necessary cookies.").needsWebView())
        assertTrue(java.io.IOException("wrapped", eu.kanade.tachiyomi.network.HttpException(403)).needsWebView())
        assertTrue(IllegalStateException("Solve the captcha in WebView").needsWebView())
        assertFalse(java.io.IOException("timeout").needsWebView())
        assertFalse(eu.kanade.tachiyomi.network.HttpException(404).needsWebView())
    }
}
