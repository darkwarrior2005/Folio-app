package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class InkStyleTest {

    @Test
    fun opacityIsFoldedIntoTheAlphaChannel() {
        val style = InkToolStyle(argb = 0xFFFDD835, widthIndex = 1, opacityPercent = 40)
        val argb = InkStyle.strokeArgb(style)
        assertEquals(102, argb ushr 24)
        assertEquals(0xFDD835, argb and 0xFFFFFF)
        assertEquals(40, InkStyle.opacityPercentOf(argb))
    }

    @Test
    fun fullAndMinimumOpacityRoundTrip() {
        assertEquals(255, InkStyle.strokeArgb(InkToolStyle(0xFF1F1F1F, 0, 100)) ushr 24)
        assertEquals(10, InkStyle.opacityPercentOf(InkStyle.strokeArgb(InkToolStyle(0xFF1F1F1F, 0, 10))))
    }

    @Test
    fun opacityOutsideTheRangeIsClamped() {
        assertEquals(26, InkStyle.strokeArgb(InkToolStyle(0xFF000000, 0, 0)) ushr 24)
        assertEquals(255, InkStyle.strokeArgb(InkToolStyle(0xFF000000, 0, 150)) ushr 24)
    }

    @Test
    fun sliderValuesSnapToFivePercentSteps() {
        assertEquals(40, InkStyle.snapOpacity(41.9f))
        assertEquals(45, InkStyle.snapOpacity(42.6f))
        assertEquals(10, InkStyle.snapOpacity(3f))
        assertEquals(100, InkStyle.snapOpacity(104f))
    }

    @Test
    fun highlighterIsWiderThanThePenAndIndexesAreClamped() {
        assertEquals(0.006f, InkStyle.widthNorm(InkTool.PEN, 1))
        assertEquals(0.025f, InkStyle.widthNorm(InkTool.HIGHLIGHTER, 1))
        assertEquals(0.012f, InkStyle.widthNorm(InkTool.PEN, 9))
        assertEquals(0.003f, InkStyle.widthNorm(InkTool.PEN, -1))
    }

    @Test
    fun defaultsMatchTheSpec() {
        assertEquals(8, InkStyle.PALETTE.size)
        assertEquals(100, InkStyle.PEN_DEFAULT.opacityPercent)
        assertEquals(40, InkStyle.HIGHLIGHTER_DEFAULT.opacityPercent)
    }
}
