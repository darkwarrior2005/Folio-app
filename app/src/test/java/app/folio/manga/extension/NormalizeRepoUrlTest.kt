package app.folio.manga.extension

import org.junit.Assert.assertEquals
import org.junit.Test

class NormalizeRepoUrlTest {

    @Test
    fun pastedIndexAddressesBecomeTheRepositoryFolder() {
        assertEquals(
            "https://raw.githubusercontent.com/keiyoushi/extensions/repo",
            normalizeRepoUrl("https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.pb"),
        )
        assertEquals(
            "https://raw.githubusercontent.com/FelipeGFA/extensoes/refs/heads/repo",
            normalizeRepoUrl(" https://raw.githubusercontent.com/FelipeGFA/extensoes/refs/heads/repo/index.pb "),
        )
        assertEquals("https://example.org/repo", normalizeRepoUrl("https://example.org/repo/index.min.json"))
        assertEquals("https://example.org/repo", normalizeRepoUrl("https://example.org/repo/repo.json"))
        assertEquals("https://example.org/repo", normalizeRepoUrl("https://example.org/repo/"))
    }
}
