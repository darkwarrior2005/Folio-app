package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderBudgetTest {

    @Test
    fun requestedZoomFitsWhenBudgetAllows() {
        // 1080 px wide, A4-ish page: 1080 x 1527 at 1x is ~6.6 MB; 3x is ~59 MB.
        assertEquals(3f, RenderBudget.maxZoom(1080, 0.707f, budgetBytes = 64L * 1024 * 1024, requested = 3f), 0.001f)
    }

    @Test
    fun zoomIsClampedToTheLargestBitmapThatFits() {
        val zoom = RenderBudget.maxZoom(1080, 0.707f, budgetBytes = 32L * 1024 * 1024, requested = 5f)
        val width = 1080 * zoom
        val height = width / 0.707f
        assert(width * height * RenderBudget.BYTES_PER_PIXEL <= 32L * 1024 * 1024)
        assert(zoom > 2f)
    }

    @Test
    fun neverBelowFit() {
        assertEquals(1f, RenderBudget.maxZoom(4000, 0.2f, budgetBytes = 1024, requested = 4f), 0.001f)
    }

    @Test
    fun defaultBudgetIsAnEighthOfTheHeap() {
        assertEquals(64L * 1024 * 1024, RenderBudget.defaultBudget(512L * 1024 * 1024))
    }
}
