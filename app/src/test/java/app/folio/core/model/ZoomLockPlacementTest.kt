package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomLockPlacementTest {

    @Test
    fun snapsToTheNearestHorizontalEdge() {
        assertEquals(NormalizedOffset(0f, 0.5f), ZoomLockPlacement.snap(0.3f, 0.5f))
        assertEquals(NormalizedOffset(1f, 0.5f), ZoomLockPlacement.snap(0.7f, 0.5f))
    }

    @Test
    fun keepsClearOfTheToolbars() {
        assertEquals(NormalizedOffset(1f, ZoomLockPlacement.MIN_Y), ZoomLockPlacement.snap(0.9f, 0.01f))
        assertEquals(NormalizedOffset(0f, ZoomLockPlacement.MAX_Y), ZoomLockPlacement.snap(0.1f, 0.99f))
    }

    @Test
    fun defaultSitsOnTheRightBelowTheMiddle() {
        assertEquals(NormalizedOffset(1f, 0.62f), ZoomLockPlacement.DEFAULT)
    }
}
