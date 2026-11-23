package app.folio.manga.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes whole index.pb files as downloaded from two real repositories (November 2026), so a
 * change that loses extensions shows up here. Counts were taken independently of this decoder.
 */
class RealRepoIndexTest {

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("repo/$name")!!.use { it.readBytes() }

    @Test
    fun everyKeiyoushiExtensionDecodes() =
        assertWholeCatalogue(resource("keiyoushi-full.pb"), extensions = 1719, adult = 494, sources = 2796)

    @Test
    fun everyFelipeGfaExtensionDecodes() =
        assertWholeCatalogue(resource("felipegfa-full.pb"), extensions = 1383, adult = 372, sources = 2406)

    private fun assertWholeCatalogue(bytes: ByteArray, extensions: Int, adult: Int, sources: Int) {
        val entries = RepoIndexV2.decode(bytes)
        assertEquals(extensions, entries.size)
        assertEquals(extensions, entries.distinctBy { it.pkgName }.size)
        assertEquals(adult, entries.count { it.contentRating == 3 })
        assertEquals(sources, entries.sumOf { it.sources.size })
        entries.forEach { e ->
            assertTrue(e.pkgName, e.pkgName.startsWith("eu.kanade.tachiyomi.extension."))
            assertTrue(e.pkgName, e.apkUrl.startsWith("https://") && e.apkUrl.endsWith(".apk"))
            assertTrue(e.pkgName, e.iconUrl.startsWith("https://"))
            assertTrue(e.pkgName, e.name.isNotBlank() && e.versionName.isNotBlank() && e.versionCode > 0)
            assertTrue(e.pkgName, extensionLang(e.pkgName, e.sources.map { it.lang }).isNotBlank())
        }
    }

    @Test
    fun theAddressesUsersPasteBecomeTheRepositoryFolder() {
        assertEquals(
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
            normalizeRepoBaseUrl("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/FelipeGFA/extensoes/refs/heads/repo",
            normalizeRepoBaseUrl(" https://raw.githubusercontent.com/FelipeGFA/extensoes/refs/heads/repo/index.pb "),
        )
    }
}
