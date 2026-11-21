package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLanguagesTest {

    @Test
    fun hiddenLanguagesAreFilteredOut() {
        val visible = SourceLanguages.visible(setOf("en", "ja", "es"), hiddenLanguages = setOf("ja"))
        assertEquals(listOf("en", "es"), visible)
    }

    @Test
    fun onlyShowHidesEveryOtherLanguage() {
        val hidden = SourceLanguages.onlyShow(setOf("en", "ja", "es"), "en")
        assertEquals(setOf("ja", "es"), hidden)
        assertFalse(SourceLanguages.isVisible("ja", hidden))
        assertTrue(SourceLanguages.isVisible("en", hidden))
    }

    @Test
    fun nothingHiddenMeansEverythingVisible() {
        assertEquals(listOf("en", "ja"), SourceLanguages.visible(setOf("ja", "en"), emptySet()))
    }
}
