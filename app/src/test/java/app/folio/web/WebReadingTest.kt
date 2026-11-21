package app.folio.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebReadingTest {

    @Test
    fun articleExtractorKeepsTheMainParagraphsAndDropsChrome() {
        val html = """
            <html><head><title>A Great Story</title></head>
            <body>
              <nav>Home | About | Contact</nav>
              <div class="sidebar">Related: other things to read right now click here</div>
              <article>
                <h1>A Great Story</h1>
                <p>Once upon a time, in a land far away, there lived a programmer who wrote tests.</p>
                <p>The programmer tested everything, and everything was good, and the suite was green.</p>
              </article>
              <div class="comments">Great post! Thanks! Nice! Agreed! Same! Totally! Wow!</div>
              <footer>Copyright 2024</footer>
            </body></html>
        """.trimIndent()

        val result = ArticleExtractor.extract(html, "https://example.com/story")
        assertEquals("A Great Story", result.title)
        assertTrue(result.html.contains("programmer who wrote tests"))
        assertTrue(result.html.contains("suite was green"))
        assertTrue(!result.html.contains("Copyright 2024"))
    }

    @Test
    fun articleExtractorFindsARelNextLink() {
        val html = """
            <html><body>
              <article><p>Page one of a long chapter that keeps going and going, on and on.</p></article>
              <a rel="next" href="/chapter-2">Next chapter</a>
            </body></html>
        """.trimIndent()
        val result = ArticleExtractor.extract(html, "https://example.com/chapter-1")
        assertEquals("https://example.com/chapter-2", result.nextUrl)
    }

    @Test
    fun resolveHandlesABareQueryLink() {
        assertEquals(
            "https://example.com/page?x=2",
            ArticleExtractor.resolve("https://example.com/page?x=1", "?x=2"),
        )
    }

    @Test
    fun feedParserReadsRss2() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0"><channel>
              <title>My Feed</title>
              <link>https://example.com</link>
              <item>
                <title>First post</title>
                <link>https://example.com/1</link>
                <description>&lt;p&gt;Hello &lt;b&gt;world&lt;/b&gt;&lt;/p&gt;</description>
                <pubDate>Mon, 01 Jan 2024 10:00:00 GMT</pubDate>
              </item>
            </channel></rss>
        """.trimIndent()
        val feed = FeedParser.parse(xml)
        assertEquals(FeedKind.RSS2, feed.kind)
        assertEquals("My Feed", feed.title)
        assertEquals(1, feed.items.size)
        assertEquals("First post", feed.items[0].title)
        assertEquals("Hello world", feed.items[0].summary)
        assertNotNull(feed.items[0].publishedAt)
    }

    @Test
    fun feedParserReadsAtom() {
        val xml = """
            <?xml version="1.0"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Feed</title>
              <entry>
                <title>Entry One</title>
                <link rel="alternate" href="https://example.com/entry-1"/>
                <summary>Just a summary</summary>
                <updated>2024-01-01T10:00:00Z</updated>
              </entry>
            </feed>
        """.trimIndent()
        val feed = FeedParser.parse(xml)
        assertEquals(FeedKind.ATOM, feed.kind)
        assertEquals(1, feed.items.size)
        assertEquals("https://example.com/entry-1", feed.items[0].link)
    }

    @Test
    fun feedDiscoveryFindsTheAlternateLink() {
        val html = """
            <html><head>
              <link rel="alternate" type="application/rss+xml" href="/feed.xml" />
            </head><body></body></html>
        """.trimIndent()
        assertEquals("https://example.com/feed.xml", FeedParser.discover(html, "https://example.com/"))
    }
}
