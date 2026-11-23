package app.folio.web

import android.content.Context
import android.net.Uri
import app.folio.data.files.CacheStore
import app.folio.data.repo.LibraryRepository
import app.folio.data.settings.SavedFeed
import app.folio.data.settings.SettingsRepository
import app.folio.data.settings.WebNovel
import app.folio.importer.ImportManager
import app.folio.importer.MetadataRescanner
import app.folio.reader.text.convert.ImageBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/** Reading from the web is switched off in Settings. */
class WebReadingOffException : IOException("Reading from the web is off")

/** The site answered, but not with something readable, or the book can no longer grow. */
class NothingToReadException(val kind: Kind) : IOException(kind.name) {
    enum class Kind { NO_TEXT, NO_FEED, FEED_UNREADABLE, NO_CHAPTER, BOOK_GONE, BOOK_LOCKED }
}

/**
 * Saves web pages, feed items and web novels as ordinary books. Pages are fetched with a client of
 * its own that keeps no cookies and no cache, read into plain HTML with their pictures inside, and
 * imported as files Folio owns. Nothing is fetched unless the user asks, and never while the
 * setting is off.
 */
class WebLibrary(
    private val context: Context,
    private val settings: SettingsRepository,
    private val imports: ImportManager,
    private val library: LibraryRepository,
    private val rescanner: MetadataRescanner,
    private val cache: CacheStore,
) {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val feedFile get() = File(context.filesDir, "web/feeds.json")

    @Serializable
    private data class FeedCache(
        val items: Map<String, List<FeedItem>> = emptyMap(),
        /** Feed item links already saved, and the book each became. */
        val saved: Map<String, Long> = emptyMap(),
    )

    // ---- Pages ---------------------------------------------------------------------------------

    /** Saves one page as a book and returns its id. */
    suspend fun savePage(url: String): Long = withContext(Dispatchers.IO) {
        val (html, finalUrl) = fetch(normalise(url))
        val article = ArticleExtractor.extract(html, finalUrl)
        if (Jsoup.parse(article.contentHtml).text().length < 80) {
            throw NothingToReadException(NothingToReadException.Kind.NO_TEXT)
        }
        val body = buildString {
            append("<h1>").append(escape(article.title)).append("</h1>\n")
            val byline = listOfNotNull(article.author, article.site).joinToString(" · ")
            if (byline.isNotEmpty()) append("<p><em>").append(escape(byline)).append("</em></p>\n")
            append(embedImages(article.contentHtml, finalUrl))
            append("\n<hr>\n<p><small>").append(escape(savedLine(finalUrl))).append("</small></p>\n")
        }
        importHtml(article.title, article.author, finalUrl, body)
    }

    // ---- Feeds ---------------------------------------------------------------------------------

    /** Adds a feed from its own address or from a site that advertises one. */
    suspend fun addFeed(url: String): SavedFeed = withContext(Dispatchers.IO) {
        val address = normalise(url)
        val (text, finalUrl) = fetch(address)
        FeedParser.parse(text, finalUrl)?.let { feed ->
            return@withContext remember(SavedFeed(finalUrl, feed.title, feed.siteUrl), feed.items)
        }
        // A web page: try the feeds it advertises.
        for (candidate in FeedParser.discover(text, finalUrl)) {
            val (xml, at) = runCatching { fetch(candidate) }.getOrNull() ?: continue
            val feed = FeedParser.parse(xml, at) ?: continue
            return@withContext remember(SavedFeed(at, feed.title, feed.siteUrl), feed.items)
        }
        throw NothingToReadException(NothingToReadException.Kind.NO_FEED)
    }

    private suspend fun remember(feed: SavedFeed, items: List<FeedItem>): SavedFeed {
        settings.update { s ->
            val others = s.web.feeds.filterNot { it.url == feed.url }
            s.copy(web = s.web.copy(feeds = others + feed))
        }
        storeItems(feed.url, items)
        return feed
    }

    suspend fun removeFeed(feed: SavedFeed) {
        settings.update { s -> s.copy(web = s.web.copy(feeds = s.web.feeds.filterNot { it.url == feed.url })) }
        withContext(Dispatchers.IO) {
            val cache = readFeedCache()
            writeFeedCache(cache.copy(items = cache.items - feed.url))
        }
    }

    suspend fun refreshFeed(feed: SavedFeed): List<FeedItem> = withContext(Dispatchers.IO) {
        val (text, finalUrl) = fetch(feed.url)
        val parsed = FeedParser.parse(text, finalUrl) ?: throw NothingToReadException(NothingToReadException.Kind.FEED_UNREADABLE)
        storeItems(feed.url, parsed.items)
        parsed.items
    }

    suspend fun cachedItems(feedUrl: String): List<FeedItem> = withContext(Dispatchers.IO) {
        readFeedCache().items[feedUrl].orEmpty()
    }

    /** Feed item links already saved as books. */
    suspend fun savedItems(): Map<String, Long> = withContext(Dispatchers.IO) { readFeedCache().saved }

    suspend fun saveItem(item: FeedItem): Long {
        val bookId = savePage(item.link)
        withContext(Dispatchers.IO) {
            val cache = readFeedCache()
            writeFeedCache(cache.copy(saved = cache.saved + (item.link to bookId)))
        }
        return bookId
    }

    private fun storeItems(feedUrl: String, items: List<FeedItem>) {
        val cache = readFeedCache()
        writeFeedCache(cache.copy(items = cache.items + (feedUrl to items.take(MAX_FEED_ITEMS))))
    }

    @Synchronized
    private fun readFeedCache(): FeedCache =
        runCatching { json.decodeFromString<FeedCache>(feedFile.readText()) }.getOrDefault(FeedCache())

    @Synchronized
    private fun writeFeedCache(cache: FeedCache) {
        feedFile.parentFile?.mkdirs()
        val tmp = File(feedFile.parentFile, "feeds.json.tmp")
        tmp.writeText(json.encodeToString(cache))
        tmp.renameTo(feedFile)
    }

    // ---- Web novels ----------------------------------------------------------------------------

    /**
     * Starts a web novel from one chapter's address: that chapter and up to [ahead] following
     * ones become a book; later chapters are added by [updateNovel].
     */
    suspend fun startNovel(url: String, ahead: Int = 10, onProgress: (Int) -> Unit = {}): Long =
        withContext(Dispatchers.IO) {
            val chapters = fetchChapters(normalise(url), ahead + 1, emptySet(), onProgress)
            val first = chapters.firstOrNull() ?: throw NothingToReadException(NothingToReadException.Kind.NO_CHAPTER)
            val title = novelTitle(first.article)
            val body = buildString {
                append("<h1>").append(escape(title)).append("</h1>\n")
                first.article.site?.let { append("<p><em>").append(escape(it)).append("</em></p>\n") }
                chapters.forEach { append(it.html) }
            }
            val bookId = importHtml(title, first.article.author, first.article.url, body)
            val last = chapters.last()
            settings.update { s ->
                val novel = WebNovel(bookId, title, last.article.url, last.article.next, chapters.size, System.currentTimeMillis())
                s.copy(web = s.web.copy(novels = s.web.novels.filterNot { it.bookId == bookId } + novel))
            }
            bookId
        }

    /** Adds the chapters published since the last check; returns how many were added. */
    suspend fun updateNovel(novel: WebNovel, max: Int = 25, onProgress: (Int) -> Unit = {}): Int =
        withContext(Dispatchers.IO) {
            val book = library.entity(novel.bookId) ?: throw NothingToReadException(NothingToReadException.Kind.BOOK_GONE)
            val path = Uri.parse(book.uri).path?.let(::File)?.takeIf { it.canWrite() }
                ?: throw NothingToReadException(NothingToReadException.Kind.BOOK_LOCKED)

            // With no next link last time, look at the last chapter again: the site may have added one.
            val start = novel.nextUrl ?: ArticleExtractor.extract(fetch(novel.lastUrl).first, novel.lastUrl).next
            if (start == null) {
                markChecked(novel, novel.lastUrl, null, 0)
                return@withContext 0
            }
            val chapters = fetchChapters(start, max, setOf(novel.lastUrl), onProgress)
            if (chapters.isEmpty()) {
                markChecked(novel, novel.lastUrl, start, 0)
                return@withContext 0
            }
            val existing = path.readText()
            val at = existing.lastIndexOf("</body>").takeIf { it >= 0 } ?: existing.length
            val added = chapters.joinToString("") { it.html }
            val tmp = File(path.parentFile, path.name + ".tmp")
            tmp.writeText(existing.substring(0, at) + added + existing.substring(at))
            if (!tmp.renameTo(path)) {
                tmp.delete()
                throw IOException("Could not update the book's file")
            }
            library.entity(novel.bookId)?.let { rescanner.rescan(it) }
            val last = chapters.last()
            markChecked(novel, last.article.url, last.article.next, chapters.size)
            chapters.size
        }

    suspend fun forgetNovel(novel: WebNovel) {
        settings.update { s -> s.copy(web = s.web.copy(novels = s.web.novels.filterNot { it.bookId == novel.bookId })) }
    }

    private suspend fun markChecked(novel: WebNovel, lastUrl: String, nextUrl: String?, added: Int) {
        settings.update { s ->
            s.copy(
                web = s.web.copy(
                    novels = s.web.novels.map {
                        if (it.bookId != novel.bookId) it
                        else it.copy(lastUrl = lastUrl, nextUrl = nextUrl, chapters = it.chapters + added, checkedAt = System.currentTimeMillis())
                    },
                ),
            )
        }
    }

    private class Chapter(val article: Article, val html: String)

    private suspend fun fetchChapters(start: String, max: Int, seen: Set<String>, onProgress: (Int) -> Unit): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val visited = seen.toMutableSet()
        var url: String? = start
        while (url != null && chapters.size < max && visited.add(url.substringBefore('#'))) {
            kotlin.coroutines.coroutineContext.ensureActive()
            val (html, finalUrl) = try {
                fetch(url)
            } catch (e: IOException) {
                // Keep what was fetched so far; the next check carries on from here.
                if (chapters.isEmpty()) throw e else break
            }
            val article = ArticleExtractor.extract(html, finalUrl)
            if (Jsoup.parse(article.contentHtml).text().length < 40) break
            val chapterHtml = buildString {
                append("<h2>").append(escape(article.title)).append("</h2>\n")
                append(embedImages(article.contentHtml, finalUrl))
                append('\n')
            }
            chapters += Chapter(article, chapterHtml)
            onProgress(chapters.size)
            url = article.next
        }
        return chapters
    }

    /** The novel's name: the site's name for it when the chapter title carries one, else the title. */
    private fun novelTitle(first: Article): String {
        val title = first.title
        val split = title.split(Regex(" [-–—:|] "))
        return if (split.size > 1 && split.first().length >= 3 && !split.first().contains(Regex("(?i)chapter|ch\\.|第"))) {
            split.first()
        } else {
            title
        }
    }

    // ---- Shared --------------------------------------------------------------------------------

    private suspend fun fetch(url: String): Pair<String, String> {
        if (!settings.current().web.enabled) throw WebReadingOffException()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/rss+xml,*/*;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The site answered ${response.code}")
            val body = response.body
            val bytes = body.byteStream().use { readLimited(it, MAX_PAGE_BYTES) }
            val charset = body.contentType()?.charset() ?: sniffCharset(bytes)
            return String(bytes, charset) to response.request.url.toString()
        }
    }

    /** Pictures are fetched and kept inside the page; ones that cannot be fetched are left out. */
    private suspend fun embedImages(html: String, pageUrl: String): String {
        val fragment = Jsoup.parseBodyFragment(html)
        val budget = ImageBudget(MAX_PAGE_IMAGES_BYTES)
        fragment.select("img").forEachIndexed { index, img ->
            kotlin.coroutines.coroutineContext.ensureActive()
            val src = img.attr("src")
            val data = if (index < MAX_IMAGES) runCatching { fetchImage(src, pageUrl) }.getOrNull() else null
            val uri = budget.dataUri(data)
            if (uri != null) {
                img.attr("src", uri)
            } else {
                val alt = img.attr("alt").trim()
                if (alt.isNotEmpty()) img.replaceWith(org.jsoup.nodes.TextNode("[$alt]")) else img.remove()
            }
        }
        return fragment.body().html()
    }

    private suspend fun fetchImage(url: String, referer: String): ByteArray? {
        if (!settings.current().web.enabled) throw WebReadingOffException()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).header("Referer", referer).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body.byteStream().use { readLimited(it, MAX_IMAGE_BYTES) }
        }
    }

    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > limit) throw IOException("Too large")
        }
        return out.toByteArray()
    }

    private fun sniffCharset(bytes: ByteArray): java.nio.charset.Charset {
        val head = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val declared = Regex("(?i)charset=[\"']?([\\w-]+)").find(head)?.groupValues?.get(1)
            ?: Regex("(?i)encoding=[\"']([\\w-]+)").find(head)?.groupValues?.get(1)
        return declared?.let { runCatching { java.nio.charset.Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
    }

    private suspend fun importHtml(title: String, author: String?, source: String, body: String): Long {
        val staging = File(cache.tmpDir, "web").apply { mkdirs() }
        staging.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
        val name = fileName(title)
        val file = File(staging, name)
        file.writeText(
            buildString {
                append("<!doctype html>\n<html><head><meta charset=\"utf-8\">\n")
                append("<title>").append(escape(title)).append("</title>\n")
                author?.let { append("<meta name=\"author\" content=\"").append(escape(it)).append("\">\n") }
                append("<meta name=\"folio-source\" content=\"").append(escape(source)).append("\">\n")
                append("</head><body>\n").append(body).append("</body></html>\n")
            },
        )
        val bookId = imports.importOwn(Uri.fromFile(file).toString())
        file.delete()
        return bookId ?: throw IOException("The page could not be added to the library")
    }

    private fun fileName(title: String): String {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().take(80)
        return (safe.ifEmpty { "Web page" }) + " (" + System.currentTimeMillis().toString(36) + ").html"
    }

    private fun savedLine(url: String): String =
        "Saved from $url on " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())

    private fun normalise(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
        private const val MAX_PAGE_BYTES = 8 * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        private const val MAX_PAGE_IMAGES_BYTES = 12L * 1024 * 1024
        private const val MAX_IMAGES = 40
        private const val MAX_FEED_ITEMS = 100

        /** The first web address in shared text ("Look at this: https://…"). */
        fun findUrl(text: String?): String? =
            text?.let { Regex("https?://[^\\s<>\"']+").find(it)?.value?.trimEnd('.', ',', ')', ']', '!', '?') }
    }
}
