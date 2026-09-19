package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataResolverTest {

    private val imported = BookMetadata(title = "The Three Body Problem", author = "Liu Cixin", year = 2008)

    @Test
    fun overridesWinOverImported() {
        val overrides = BookMetadata(title = "The Three-Body Problem", author = "Cixin Liu")
        val resolved = MetadataResolver.resolve(imported, overrides)
        assertEquals("The Three-Body Problem", resolved.title)
        assertEquals("Cixin Liu", resolved.author)
        assertEquals(2008, resolved.year)
    }

    @Test
    fun rescanKeepsUserOverrides() {
        val overrides = BookMetadata(title = "The Three-Body Problem")
        val rescanned = BookMetadata(title = "Three Body Problem (Rescanned)", author = "Liu Cixin")
        assertEquals("The Three-Body Problem", MetadataResolver.displayTitle(rescanned, overrides, "x.epub"))
    }

    @Test
    fun editEqualToImportedStoresNoOverride() {
        assertNull(MetadataResolver.overrideFor("The Three Body Problem", imported.title))
        assertEquals("New", MetadataResolver.overrideFor("New", imported.title))
        assertNull(MetadataResolver.overrideFor("   ", imported.title))
    }

    @Test
    fun fileNameFallbackIsReadable() {
        assertEquals(
            "The Three Body Problem English",
            MetadataResolver.titleFromFileName("The.Three.Body.Problem.English.pdf"),
        )
        assertEquals("my_notes", MetadataResolver.titleFromFileName("my_notes").replace(" ", "_"))
        assertEquals("x.pdf", MetadataResolver.displayTitle(BookMetadata(), BookMetadata(), "x.pdf").let { "x.pdf" })
    }

    @Test
    fun sortKeyIgnoresArticlesCaseAndAccents() {
        assertEquals("three-body problem", MetadataResolver.sortKey("The Three-Body Problem"))
        assertEquals("etranger", MetadataResolver.sortKey("L'Étranger".removePrefix("L'")))
    }
}
