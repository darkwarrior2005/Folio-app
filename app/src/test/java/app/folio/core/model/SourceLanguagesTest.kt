package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLanguagesTest {

    @Test
    fun `nothing hidden shows everything`() {
        assertTrue(SourceLanguages.isShown("ja", emptySet()))
        assertTrue(SourceLanguages.isExtensionShown("ja", listOf("ja"), emptySet()))
    }

    @Test
    fun `a hidden language is not shown and an unnamed one counts as other`() {
        assertFalse(SourceLanguages.isShown("ja", setOf("ja")))
        assertFalse(SourceLanguages.isShown("", setOf("other")))
        assertTrue(SourceLanguages.isShown("en", setOf("ja")))
    }

    @Test
    fun `a multi-language extension stays while any of its languages is kept`() {
        assertTrue(SourceLanguages.isExtensionShown("all", listOf("ja", "en"), setOf("ja")))
        assertFalse(SourceLanguages.isExtensionShown("all", listOf("ja", "de"), setOf("ja", "de")))
    }

    @Test
    fun `hidden languages stay in the choices so they can be turned back on`() {
        assertEquals(listOf("en", "other", "ja"), SourceLanguages.choices(listOf("en", ""), setOf("ja")))
    }

    @Test
    fun `keeping one language keeps its regional variants and multi-language sources`() {
        val hidden = SourceLanguages.hideAllBut(listOf("en", "pt", "pt-BR", "ja", "all"), keep = listOf("pt"))
        assertEquals(setOf("en", "ja"), hidden)
    }
}
