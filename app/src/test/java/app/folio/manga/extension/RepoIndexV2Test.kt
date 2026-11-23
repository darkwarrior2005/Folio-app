package app.folio.manga.extension

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoIndexV2Test {

    /** Encodes protobuf wire format, the way a repository's index.pb is built. */
    private class Proto {
        private val out = ByteArrayOutputStream()

        fun varint(field: Int, value: Long) = apply {
            tag(field, 0)
            raw(value)
        }

        fun string(field: Int, value: String) = bytes(field, value.toByteArray())

        fun message(field: Int, build: Proto.() -> Unit) = bytes(field, Proto().apply(build).toByteArray())

        fun fixed32(field: Int) = apply {
            tag(field, 5)
            out.write(ByteArray(4))
        }

        fun fixed64(field: Int) = apply {
            tag(field, 1)
            out.write(ByteArray(8))
        }

        fun toByteArray(): ByteArray = out.toByteArray()

        private fun bytes(field: Int, value: ByteArray) = apply {
            tag(field, 2)
            raw(value.size.toLong())
            out.write(value)
        }

        private fun tag(field: Int, wireType: Int) = raw(((field shl 3) or wireType).toLong())

        private fun raw(value: Long) {
            var rest = value
            while (rest and 0x7FL.inv() != 0L) {
                out.write(((rest and 0x7F) or 0x80).toInt())
                rest = rest ushr 7
            }
            out.write(rest.toInt())
        }
    }

    private fun index(): ByteArray = Proto().apply {
        string(1, "Keiyoushi")
        string(2, "KEI")
        string(3, "9add655a")
        message(4) {
            string(1, "https://keiyoushi.github.io")
            string(2, "https://discord.gg/x")
        }
        message(101) {
            message(1) {
                string(1, "Akuma")
                string(2, "eu.kanade.tachiyomi.extension.all.akuma")
                message(3) {
                    string(1, "https://example.org/akuma.apk")
                    string(2, "https://example.org/akuma.png")
                    string(501, "https://example.org/akuma.jar")
                }
                string(4, "1.6")
                varint(5, 106010)
                string(6, "1.6.10")
                varint(7, 3)
                message(8) {
                    varint(1, 1843646695111412994)
                    string(2, "Akuma")
                    string(3, "en")
                    string(4, "https://akuma.moe")
                    string(5, "https://mirror.akuma.moe")
                }
                message(8) {
                    varint(1, -42)
                    string(2, "Akuma")
                    string(3, "fr")
                    string(4, "https://akuma.moe")
                }
                // Fields a later index might add must be skipped, whatever their wire type.
                fixed32(90)
                fixed64(91)
                varint(92, 7)
            }
            message(1) {
                string(1, "Tachiyomi: Cubari")
                string(2, "eu.kanade.tachiyomi.extension.en.cubari")
                message(3) {
                    string(1, "https://example.org/cubari.apk")
                    string(2, "https://example.org/cubari.png")
                }
                string(4, "1.4")
                varint(5, 14)
                string(6, "1.4.14")
                varint(7, 1)
            }
        }
    }.toByteArray()

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()

    @Test
    fun `reads every extension with its urls, versions and sources`() {
        val extensions = RepoIndexV2.decode(index())

        assertEquals(listOf("eu.kanade.tachiyomi.extension.all.akuma", "eu.kanade.tachiyomi.extension.en.cubari"), extensions.map { it.pkgName })

        val akuma = extensions.first()
        assertEquals("Akuma", akuma.name)
        assertEquals("https://example.org/akuma.apk", akuma.apkUrl)
        assertEquals("https://example.org/akuma.png", akuma.iconUrl)
        assertEquals("1.6", akuma.libVersion)
        assertEquals(106010L, akuma.versionCode)
        assertEquals("1.6.10", akuma.versionName)
        assertEquals(3, akuma.contentRating)
        assertEquals(listOf("en", "fr"), akuma.sources.map { it.lang })
        assertEquals(1843646695111412994L, akuma.sources[0].id)
        assertEquals(-42L, akuma.sources[1].id)
        assertEquals("https://akuma.moe", akuma.sources[0].baseUrl)

        val cubari = extensions[1]
        assertEquals("Tachiyomi: Cubari", cubari.name)
        assertEquals(1, cubari.contentRating)
        assertTrue(cubari.sources.isEmpty())
    }

    @Test
    fun `reads a gzip-compressed index the same as a plain one`() {
        assertEquals(RepoIndexV2.decode(index()), RepoIndexV2.decode(gzip(index())))
    }

    @Test
    fun `an index without extensions is empty rather than an error`() {
        val bytes = Proto().apply { string(1, "Empty") }.toByteArray()
        assertTrue(RepoIndexV2.decode(bytes).isEmpty())
    }

    @Test
    fun `a truncated index is rejected`() {
        val bytes = index()
        assertThrows(IllegalStateException::class.java) {
            RepoIndexV2.decode(bytes.copyOf(bytes.size - 5))
        }
    }

    @Test
    fun `accepts the base url or any index file people paste`() {
        val keiyoushi = "https://raw.githubusercontent.com/keiyoushi/extensions/repo"
        assertEquals(keiyoushi, normalizeRepoBaseUrl("$keiyoushi/index.pb"))
        assertEquals(keiyoushi, normalizeRepoBaseUrl("$keiyoushi/index.min.json"))
        assertEquals(keiyoushi, normalizeRepoBaseUrl("$keiyoushi/repo.json"))
        assertEquals(keiyoushi, normalizeRepoBaseUrl("  $keiyoushi/  "))
        assertEquals(keiyoushi, normalizeRepoBaseUrl(keiyoushi))

        val felipe = "https://raw.githubusercontent.com/FelipeGFA/extensoes/refs/heads/repo"
        assertEquals(felipe, normalizeRepoBaseUrl("$felipe/index.pb"))
    }

    @Test
    fun `rejects a url that is not http`() {
        assertThrows(IllegalArgumentException::class.java) { normalizeRepoBaseUrl("ftp://example.org/repo") }
    }

    @Test
    fun `language comes from the sources, else from the package name`() {
        assertEquals("all", extensionLang("eu.kanade.tachiyomi.extension.all.akuma", listOf("en", "fr")))
        assertEquals("pt-BR", extensionLang("eu.kanade.tachiyomi.extension.pt.amuy", listOf("pt-BR")))
        assertEquals("en", extensionLang("eu.kanade.tachiyomi.extension.all.mango", listOf("en", "en")))
        assertEquals("ja", extensionLang("eu.kanade.tachiyomi.extension.ja.site", emptyList()))
        assertEquals("", extensionLang("org.example.site", emptyList()))
    }
}
