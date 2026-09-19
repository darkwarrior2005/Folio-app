package app.folio.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentColorTest {

    @Test
    fun decodesTheUserStoredValueBackToItsArgb() {
        // The exact value found stored on the user's device: a packed Color.value with
        // 0xFFC1553B in the high 32 bits and zero in the low 32 bits.
        val stored = -17639211641339904L
        assertEquals(0xFFC1553B.toInt(), AccentColor.decode(stored).toArgb())
    }

    @Test
    fun theOldNaiveDecodeWouldHaveBeenFullyTransparent() {
        // This is the bug the controller found: Color(it.toInt()) only looks at the low 32 bits
        // of the packed value, which are always zero for an sRGB colour, so it decoded to alpha 0
        // instead of the picked accent.
        val stored = -17639211641339904L
        val old = Color(stored.toInt())
        assertEquals(0, old.toArgb() ushr 24)
        // Whereas the fix keeps the real alpha.
        assertEquals(0xFF, AccentColor.decode(stored).toArgb() ushr 24)
    }

    @Test
    fun encodeAndDecodeRoundTripAnOpaqueColour() {
        val original = Color(0xFF4285F4)
        val roundTripped = AccentColor.decode(AccentColor.encode(original))
        assertEquals(original.toArgb(), roundTripped.toArgb())
    }

    @Test
    fun decodesAPlainArgbIntDefensively() {
        // A value with an empty high word isn't a packed Color.value; treat it as a raw ARGB int
        // instead of letting it decode to transparent black.
        val plainArgb = 0xFF123456L
        assertEquals(0xFF123456.toInt(), AccentColor.decode(plainArgb).toArgb())
    }
}
