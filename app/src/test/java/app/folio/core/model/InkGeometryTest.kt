package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkGeometryTest {

    @Test
    fun packingRoundTripsPointsExactly() {
        val points = listOf(InkPoint(0f, 0f), InkPoint(0.25f, 0.5f), InkPoint(1f, 0.999f))
        val bytes = InkPacking.pack(points)
        assertEquals(24, bytes.size)
        assertEquals(points, InkPacking.unpack(bytes))
    }

    @Test
    fun simplifyDropsPointsOnAStraightLine() {
        val line = (0..20).map { InkPoint(it / 20f, 0.5f) }
        assertEquals(listOf(line.first(), line.last()), StrokeSimplifier.simplify(line))
    }

    @Test
    fun simplifyKeepsACorner() {
        val corner = listOf(InkPoint(0.1f, 0.1f), InkPoint(0.3f, 0.1f), InkPoint(0.5f, 0.1f), InkPoint(0.5f, 0.3f), InkPoint(0.5f, 0.5f))
        assertEquals(
            listOf(InkPoint(0.1f, 0.1f), InkPoint(0.5f, 0.1f), InkPoint(0.5f, 0.5f)),
            StrokeSimplifier.simplify(corner),
        )
    }

    @Test
    fun simplifyLeavesShortStrokesAlone() {
        val dot = listOf(InkPoint(0.2f, 0.2f))
        assertEquals(dot, StrokeSimplifier.simplify(dot))
    }

    @Test
    fun hitCountsTheStrokeWidthAndEraserRadius() {
        val stroke = listOf(InkPoint(0.1f, 0.5f), InkPoint(0.9f, 0.5f))
        assertTrue(StrokeHit.hits(stroke, widthNorm = 0.02f, touch = InkPoint(0.5f, 0.52f), radius = 0.015f, heightOverWidth = 1f))
        assertFalse(StrokeHit.hits(stroke, widthNorm = 0.02f, touch = InkPoint(0.5f, 0.6f), radius = 0.015f, heightOverWidth = 1f))
    }

    @Test
    fun hitMeasuresVerticalDistanceInPageWidths() {
        // On a tall page (height = 1.5 widths) 0.02 of the height is 0.03 widths away.
        val stroke = listOf(InkPoint(0.1f, 0.5f), InkPoint(0.9f, 0.5f))
        assertTrue(StrokeHit.hits(stroke, 0f, InkPoint(0.5f, 0.52f), radius = 0.025f, heightOverWidth = 1f))
        assertFalse(StrokeHit.hits(stroke, 0f, InkPoint(0.5f, 0.52f), radius = 0.025f, heightOverWidth = 1.5f))
    }

    @Test
    fun hitWorksForASingleDotAndIgnoresEmptyStrokes() {
        assertTrue(StrokeHit.hits(listOf(InkPoint(0.5f, 0.5f)), 0.01f, InkPoint(0.505f, 0.5f), 0.01f, 1f))
        assertFalse(StrokeHit.hits(emptyList(), 0.01f, InkPoint(0.5f, 0.5f), 0.01f, 1f))
    }

    @Test
    fun pageFrameMapsThroughTheCrop() {
        val crop = NormRect(0.1f, 0.2f, 0.9f, 0.8f)
        val page = PageFrame.boxToPage(InkPoint(0.5f, 0.5f), crop)
        assertEquals(0.5f, page.x, 1e-5f)
        assertEquals(0.5f, page.y, 1e-5f)
        val corner = PageFrame.boxToPage(InkPoint(0f, 1f), crop)
        assertEquals(0.1f, corner.x, 1e-5f)
        assertEquals(0.8f, corner.y, 1e-5f)
        val back = PageFrame.pageToBox(corner, crop)
        assertEquals(0f, back.x, 1e-5f)
        assertEquals(1f, back.y, 1e-5f)
        assertEquals(InkPoint(0.3f, 0.4f), PageFrame.boxToPage(InkPoint(0.3f, 0.4f), null))
    }

    @Test
    fun heightOverWidthUsesTheWholePageShape() {
        // A 400x300 box showing 80% of the width and 60% of the height: page is 500x500.
        assertEquals(1f, PageFrame.heightOverWidth(400f, 300f, NormRect(0.1f, 0.2f, 0.9f, 0.8f)), 1e-4f)
        assertEquals(1.5f, PageFrame.heightOverWidth(200f, 300f, null), 1e-4f)
    }

    @Test
    fun drawnNotesNearTheCurrentPositionAreFound() {
        val notes = listOf(1L to 0.100f, 2L to 0.108f, 3L to 0.2f)
        assertEquals(listOf(1L, 2L), DrawnNoteProximity.near(notes, 0.1f) { it.second }.map { it.first })
    }
}
