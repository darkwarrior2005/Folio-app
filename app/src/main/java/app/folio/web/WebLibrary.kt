package app.folio.web

import android.content.Context
import app.folio.AppContainer
import app.folio.data.settings.SavedFeed
import app.folio.data.settings.WebNovel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.time.Duration.Companion.seconds

sealed class NothingToReadException(message: String) : Exception(message) {
    class WebReadingOff : NothingToReadException("From the web is turned off")
    class TooLarge : NothingToReadException("That page is too large")
    class TooManyImages : NothingToReadException("That page has too many pictures")
    class NetworkFailed(cause: Throwable) : NothingToReadException(cause.message ?: "Couldn't reach that page")
    class NoFeedFound : NothingToReadException("No RSS or Atom feed found on that page")
}

private const val MAX_PAGE_BYTES = 8 * 1024 * 1024
private const val MAX_IMAGES_PER_PAGE = 40
private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
private const val MAX_TOTAL_IMAGE_BYTES = 12 * 1024 * 1024

/**
 * The network layer for "From the web". Keeps no cookie jar and no cache of its own - deliberately
 * separate from the manga NetworkHelper - and every request checks [WebSettings.enabled] first.
 */
class WebLibrary(private val context: Context, private val container: AppContainer) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20.seconds)
        .readTimeout(20.seconds)
        .build()

    private suspend fun requireEnabled() {
        if (!container.settings.current().web.enabled) throw NothingToReadException.WebReadingOff()
    }

    private suspend fun fetchText(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        requireEnabled()
        val response = try {
            client.newCall(Request.Builder().url(url).build()).execute()
        } catch (e: Exception) {
            throw NothingToReadException.NetworkFailed(e)
        }
        response.use {
            val body = it.body ?: throw NothingToReadException.NetworkFailed(IllegalStateException("Empty response"))
            val bytes = readUpTo(body.byteStream(), MAX_PAGE_BYTES) ?: throw NothingToReadException.TooLarge()
            String(bytes, Charsets.UTF_8) to (it.request.url.toString())
        }
    }

    /** Saves a link as a book: title/author meta, and up to 40 inlined pictures, 4MB/12MB caps. */
    suspend fun savePage(url: String): Long = withContext(Dispatchers.IO) {
        val (html, finalUrl) = fetchText(url)
        val article = ArticleExtractor.extract(html, finalUrl)
        val withImages = inlineImages(article.html)

        val file = writeHtmlFile(
            title = article.title ?: "Saved page",
            author = article.author,
            source = finalUrl,
            bodyHtml = withImages,
        )
        container.imports.importOwn(android.net.Uri.fromFile(file).toString()).await() ?: -1L
    }

    private suspend fun inlineImages(html: String): String = withContext(Dispatchers.IO) {
        val document = org.jsoup.Jsoup.parseBodyFragment(html)
        var totalBytes = 0
        var count = 0
        document.select("img").forEach { img ->
            if (count >= MAX_IMAGES_PER_PAGE || totalBytes >= MAX_TOTAL_IMAGE_BYTES) {
                img.remove()
                return@forEach
            }
            val src = img.attr("src")
            val bytes = try {
                client.newCall(Request.Builder().url(src).build()).execute().use { response ->
                    response.body?.byteStream()?.let { readUpTo(it, MAX_IMAGE_BYTES) }
                }
            } catch (e: Exception) {
                null
            }
            if (bytes == null) {
                img.remove()
            } else {
                val mime = app.folio.reader.text.convert.guessImageMimeType(bytes)
                img.attr("src", "data:$mime;base64,${java.util.Base64.getEncoder().encodeToString(bytes)}")
                totalBytes += bytes.size
                count++
            }
        }
        document.body().html()
    }

    suspend fun feedItems(feedUrl: String): ParsedFeed = withContext(Dispatchers.IO) {
        val (xml, _) = fetchText(feedUrl)
        FeedParser.parse(xml)
    }

    /** A page's own address, if it's not a feed but links to one. */
    suspend fun discoverFeed(pageUrl: String): String? = withContext(Dispatchers.IO) {
        val (html, finalUrl) = fetchText(pageUrl)
        FeedParser.discover(html, finalUrl)
    }

    suspend fun saveFeedItem(item: FeedItem): Long = savePage(item.link)

    /** Fetches [chapterUrl] and the next [extraChapters] via rel=next, as one book. */
    suspend fun saveWebNovel(chapterUrl: String, title: String, extraChapters: Int = 10): Long = withContext(Dispatchers.IO) {
        var url: String? = chapterUrl
        var count = 0
        val chapters = mutableListOf<String>()
        while (url != null && count <= extraChapters) {
            val (html, finalUrl) = fetchText(url)
            val article = ArticleExtractor.extract(html, finalUrl)
            chapters += inlineImages(article.html)
            url = article.nextUrl
            count++
        }
        val file = writeHtmlFile(title = title, author = null, source = chapterUrl, bodyHtml = chapters.joinToString("<hr>"))
        container.imports.importOwn(android.net.Uri.fromFile(file).toString()).await() ?: -1L
    }

    /** Appends up to 25 more chapters to an existing web novel's file, following rel=next. */
    suspend fun checkForNewChapters(novel: WebNovel): WebNovel = withContext(Dispatchers.IO) {
        var url: String? = novel.nextUrl ?: return@withContext novel
        var added = 0
        val appended = StringBuilder()
        while (url != null && added < 25) {
            val (html, finalUrl) = fetchText(url)
            val article = ArticleExtractor.extract(html, finalUrl)
            appended.append("<hr>").append(inlineImages(article.html))
            url = article.nextUrl
            added++
        }
        if (added > 0) {
            appendToBook(novel.bookId, appended.toString())
            container.database.books().get(novel.bookId)?.let { container.rescanner.rescan(it) }
        }
        novel.copy(nextUrl = url, chapters = novel.chapters + added, checkedAt = System.currentTimeMillis())
    }

    /** Appends [html] right before `</body>` of the book's own file, atomically (write, then rename). */
    private fun appendToBook(bookId: Long, html: String) {
        val book = runBlocking { container.database.books().get(bookId) } ?: return
        val uri = android.net.Uri.parse(book.uri)
        val path = uri.path ?: return
        val file = File(path)
        if (!file.exists()) return
        val content = file.readText()
        val insertAt = content.lastIndexOf("</body>")
        val updated = if (insertAt >= 0) content.substring(0, insertAt) + html + content.substring(insertAt) else content + html
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(updated)
        temp.renameTo(file)
    }

    private fun writeHtmlFile(title: String, author: String?, source: String, bodyHtml: String): File {
        val dir = File(context.filesDir, "web").apply { mkdirs() }
        val safeName = title.replace(Regex("""[/\\:*?"<>|]"""), "_").take(80).ifBlank { "page" }
        val file = File(dir, "$safeName-${System.currentTimeMillis()}.html")
        file.writeText(
            buildString {
                append("<html><head><title>").append(escape(title)).append("</title>")
                author?.let { append("<meta name=\"author\" content=\"").append(escape(it)).append("\">") }
                append("<meta name=\"folio-source\" content=\"").append(escape(source)).append("\">")
                append("</head><body>").append(bodyHtml).append("</body></html>")
            },
        )
        return file
    }

    private fun escape(text: String) = text.replace("&", "&amp;").replace("\"", "&quot;")

    /** Reads at most [max] bytes; returns null if the stream has more than that. */
    private fun readUpTo(stream: java.io.InputStream, max: Int): ByteArray? = stream.use {
        val buffer = java.io.ByteArrayOutputStream(minOf(max, 64 * 1024))
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = it.read(chunk)
            if (read < 0) break
            total += read
            if (total > max) return null
            buffer.write(chunk, 0, read)
        }
        buffer.toByteArray()
    }
}
