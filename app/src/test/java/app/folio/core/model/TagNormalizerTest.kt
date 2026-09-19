package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TagNormalizerTest {

    @Test
    fun casingAndSpacingVariantsShareOneKey() {
        val keys = listOf("Machine Learning", "machine learning", "MACHINE LEARNING", "  Machine   Learning ")
            .map(TagNormalizer::key)
            .toSet()
        assertEquals(setOf("machine learning"), keys)
    }

    @Test
    fun cleanKeepsUserCasing() {
        assertEquals("Machine Learning", TagNormalizer.clean("  Machine   Learning "))
    }

    @Test
    fun suggestionsRankPrefixMatchesFirst() {
        val tags = listOf("Productivity", "Programming Languages", "Programming", "Improve", "Python")
        val result = TagNormalizer.suggest("pro", tags, { it })
        assertEquals(listOf("Programming", "Productivity", "Programming Languages", "Improve"), result)
    }

    @Test
    fun suggestionsExcludeAlreadyAppliedTags() {
        val tags = listOf("Programming", "Productivity")
        val result = TagNormalizer.suggest("pro", tags, { it }, exclude = setOf("programming"))
        assertEquals(listOf("Productivity"), result)
    }
}
