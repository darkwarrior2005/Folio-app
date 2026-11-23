package app.folio.manga.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoIndexV2Test {

    /** The real Keiyoushi index.pb header with three of its extensions, re-encoded and gzipped. */
    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("repo/$name")!!.use { it.readBytes() }

    private fun sample(): ByteArray = resource("keiyoushi-sample.pb")

    /** Whole index.pb files as downloaded from each repository (Nov 2026 snapshots). */
    @Test
    fun readsTheWholeKeiyoushiCatalogue() =
        assertWholeCatalogue(resource("keiyoushi-full.pb"), "Keiyoushi", extensions = 1719, adult = 494, sources = 2796)

    @Test
    fun readsTheWholeFelipeGfaCatalogue() =
        assertWholeCatalogue(resource("felipegfa-full.pb"), "FelipeGFA", extensions = 1383, adult = 372, sources = 2406)

    private fun assertWholeCatalogue(bytes: ByteArray, name: String?, extensions: Int, adult: Int, sources: Int) {
        val index = RepoIndexV2.decode(bytes)
        if (name != null) assertEquals(name, index.name)
        assertEquals(extensions, index.extensions.size)
        assertEquals(adult, index.extensions.count { it.isNsfw })
        assertEquals(sources, index.extensions.sumOf { it.sources.size })
        index.extensions.forEach { ext ->
            assertTrue(ext.pkg, ext.pkg.startsWith("eu.kanade.tachiyomi.extension."))
            assertTrue(ext.pkg, ext.apkUrl.startsWith("https://") && ext.apkUrl.endsWith(".apk"))
            assertTrue(ext.pkg, ext.name.isNotBlank() && ext.versionName.isNotBlank() && ext.versionCode > 0)
            assertTrue(ext.pkg, ext.lang.isNotBlank())
        }
        assertEquals(extensions, index.extensions.distinctBy { it.pkg }.size)
    }

    @Test
    fun readsTheRepositoryHeader() {
        val index = RepoIndexV2.decode(sample())
        assertEquals("Keiyoushi", index.name)
        assertEquals("KEI", index.shortName)
        assertEquals("9add655a78e96c4ec7a53ef89dccb557cb5d767489fac5e785d671a5a75d4da2", index.signingKeyFingerprint)
        assertEquals("https://keiyoushi.github.io", index.website)
    }

    @Test
    fun readsEveryExtensionWithItsDetails() {
        val extensions = RepoIndexV2.decode(sample()).extensions
        assertEquals(listOf("AchyManga", "AHottie", "HentaiFox"), extensions.map { it.name })

        val achy = extensions.first()
        assertEquals("eu.kanade.tachiyomi.extension.all.achymanga", achy.pkg)
        assertEquals(
            "https://github.com/keiyoushi/extensions/releases/download/b4f9106-0/tachiyomi-all.achymanga-v1.6.1.apk",
            achy.apkUrl,
        )
        assertTrue(achy.iconUrl.endsWith("/achymanga/res/mipmap-xhdpi/ic_launcher.png"))
        assertEquals("1.6", achy.libVersion)
        assertEquals("1.6.1", achy.versionName)
        assertEquals(106001L, achy.versionCode)
        assertEquals("all", achy.lang)
        assertEquals(listOf("es", "en"), achy.sources.map { it.lang })
        assertEquals(9124545289113350724L, achy.sources.first().id)
        assertEquals("https://achymanga.online", achy.sources.first().baseUrl)
    }

    @Test
    fun contentRatingThreeIsAdult() {
        val byName = RepoIndexV2.decode(sample()).extensions.associateBy { it.name }
        assertFalse(byName.getValue("AchyManga").isNsfw)
        assertTrue(byName.getValue("HentaiFox").isNsfw)
    }

    @Test
    fun acceptsAnAlreadyDecompressedIndex() {
        val raw = java.util.zip.GZIPInputStream(sample().inputStream()).use { it.readBytes() }
        assertEquals(3, RepoIndexV2.decode(raw).extensions.size)
    }

    @Test
    fun rejectsSomethingThatIsNotAnIndex() {
        assertThrows(Exception::class.java) { RepoIndexV2.decode("<html>Not found</html>".toByteArray()) }
    }
}
