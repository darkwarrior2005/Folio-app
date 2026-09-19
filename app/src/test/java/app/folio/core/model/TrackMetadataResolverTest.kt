package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMetadataResolverTest {

    @Test
    fun userValuesWinOverImported() {
        val imported = TrackMetadata(title = "Track 01", artist = "Unknown", album = "Rain")
        val overrides = TrackMetadata(title = "Rainy Library")
        val resolved = TrackMetadataResolver.resolve(imported, overrides)
        assertEquals("Rainy Library", resolved.title)
        assertEquals("Unknown", resolved.artist)
        assertEquals("Rain", resolved.album)
    }

    @Test
    fun titleFallsBackToFileName() {
        assertEquals(
            "lofi study mix",
            TrackMetadataResolver.displayTitle(TrackMetadata(), TrackMetadata(), "lofi_study_mix.mp3"),
        )
    }

    @Test
    fun blankArtistIsNoArtist() {
        assertNull(TrackMetadataResolver.displayArtist(TrackMetadata(artist = "  "), TrackMetadata()))
    }
}
