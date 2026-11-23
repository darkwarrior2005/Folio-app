package app.folio.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebReadingTest {

    private val paragraph = "This is a long paragraph of the story, with commas, clauses, and enough words to count as text. "

    private val articlePage = """
        <html><head>
          <title>The Quiet Garden - A slow essay | Example Times</title>
          <meta property="og:site_name" content="Example Times">
          <meta name="author" content="Mira Okafor">
        </head><body>
          <nav class="navbar"><a href="/">Home</a><a href="/world">World</a></nav>
          <div class="sidebar"><p>${paragraph}Sidebar text that should go.</p></div>
          <article class="post">
            <h1>The Quiet Garden - A slow essay</h1>
            <p>${paragraph.repeat(3)}</p>
            <p>${paragraph.repeat(2)}<a href="/notes">a note</a></p>
            <figure><img data-src="/img/garden.jpg" src="data:image/gif;base64,R0lGOD"></figure>
            <img src="https://track.example.org/p.gif" width="1" height="1">
            <div class="share-buttons"><a href="https://social.example/share">Share</a></div>
            <p>${paragraph.repeat(2)}</p>
          </article>
          <div id="comments"><p>${paragraph}A comment.</p></div>
          <footer>© Example</footer>
          <script>alert(1)</script>
        </body></html>
    """.trimIndent()

    @Test
    fun articleKeepsTheStoryAndDropsTheRest() {
        val article = ArticleExtractor.extract(articlePage, "https://news.example.com/2026/garden")
        assertEquals("The Quiet Garden - A slow essay", article.title)
        assertEquals("Mira Okafor", article.author)
        assertEquals("Example Times", article.site)
        val html = article.contentHtml
        assertTrue(html.contains("This is a long paragraph"))
        assertFalse(html.contains("Sidebar text"))
        assertFalse(html.contains("A comment."))
        assertFalse(html.contains("Share"))
        assertFalse(html.contains("alert"))
        assertFalse(html.contains("<h1>"))
        // Lazy pictures get their real address; tracking pixels go.
        assertEquals(listOf("https://news.example.com/img/garden.jpg"), article.imageUrls)
        assertTrue(html.contains("href=\"https://news.example.com/notes\""))
    }

    @Test
    fun chapterPagesFindTheirNextChapter() {
        val page = """
            <html><head><title>Chapter 5 - The Return | Novel Site</title></head><body>
            <div class="chapter-content">${"<p>$paragraph</p>".repeat(5)}</div>
            <div class="nav-buttons"><a href="/novel/4">Previous</a> <a class="btn" href="/novel/6">Next Chapter »</a></div>
            </body></html>
        """.trimIndent()
        val article = ArticleExtractor.extract(page, "https://novels.example.net/novel/5")
        assertEquals("Chapter 5 - The Return", article.title)
        assertEquals("https://novels.example.net/novel/6", article.next)
        assertFalse(article.contentHtml.contains("Previous"))
    }

    @Test
    fun nextLinksStayOnTheSameSite() {
        val doc = org.jsoup.Jsoup.parse(
            """<a href="https://ads.example.org/x">Next</a><link rel="next" href="?page=2"><a href="javascript:go()">›</a>""",
            "https://blog.example.com/post",
        )
        assertEquals("https://blog.example.com/post?page=2", ArticleExtractor.nextLink(doc, "https://blog.example.com/post"))
        val none = org.jsoup.Jsoup.parse("""<a href="https://elsewhere.org/2">Next</a>""", "https://a.example/1")
        assertNull(ArticleExtractor.nextLink(none, "https://a.example/1"))
    }

    @Test
    fun rssItemsWithEscapedHtmlAndDates() {
        val xml = """<?xml version="1.0"?>
            <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel>
              <title>Cozy Blog</title><link>https://cozy.example/</link>
              <item><title>First &amp; best</title><link>https://cozy.example/1</link>
                <guid isPermaLink="false">id-1</guid><pubDate>Tue, 10 Jun 2025 04:00:00 GMT</pubDate>
                <description>&lt;p&gt;Hello &lt;b&gt;world&lt;/b&gt;&lt;/p&gt;</description><dc:creator>Ann</dc:creator></item>
              <item><title>Second</title><guid>https://cozy.example/2</guid></item>
              <item><title>No link at all</title></item>
            </channel></rss>"""
        val feed = FeedParser.parse(xml, "https://cozy.example/feed.xml")!!
        assertEquals("Cozy Blog", feed.title)
        assertEquals(2, feed.items.size)
        val first = feed.items[0]
        assertEquals("id-1", first.id)
        assertEquals("First & best", first.title)
        assertEquals("Hello world", first.summary)
        assertEquals("Ann", first.author)
        assertEquals(1749528000000L, first.published)
        assertEquals("https://cozy.example/2", feed.items[1].link)
    }

    @Test
    fun atomEntries() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"><title>Atom Notes</title>
              <link rel="self" href="/atom.xml"/><link href="https://notes.example/"/>
              <entry><title type="html">One &lt;em&gt;note&lt;/em&gt;</title><id>tag:1</id>
                <link rel="alternate" href="/n/1"/><updated>2025-06-10T04:00:00Z</updated>
                <author><name>Bo</name></author><summary>Short.</summary></entry>
            </feed>"""
        val feed = FeedParser.parse(xml, "https://notes.example/atom.xml")!!
        assertEquals("Atom Notes", feed.title)
        assertEquals("https://notes.example/", feed.siteUrl)
        val entry = feed.items.single()
        assertEquals("One note", entry.title)
        assertEquals("https://notes.example/n/1", entry.link)
        assertEquals("Bo", entry.author)
        assertEquals(1749528000000L, entry.published)
    }

    @Test
    fun feedsAreDiscoveredFromAHomePage() {
        val html = """<html><head>
            <link rel="alternate" type="application/rss+xml" href="/feed/">
            <link rel="alternate" type="text/html" hreflang="fr" href="/fr/">
            <link rel="alternate" type="application/atom+xml" href="https://site.example/atom">
            </head></html>"""
        assertEquals(
            listOf("https://site.example/feed/", "https://site.example/atom"),
            FeedParser.discover(html, "https://site.example/"),
        )
        assertNull(FeedParser.parse("<html><body>not a feed</body></html>", "https://x.example/"))
    }
}
