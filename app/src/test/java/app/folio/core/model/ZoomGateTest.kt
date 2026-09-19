package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoomGateTest {

    @Test
    fun lockedGestureIsIgnoredWithoutAnyOutcome() {
        assertNull(ZoomGate.magnify(current = 2f, factor = 1.5f, locked = true))
        assertNull(ZoomGate.textSize(startPercent = 100, cumulativeFactor = 1.5f, locked = true))
        assertNull(ZoomGate.toggleDoubleTap(current = 1f, locked = true))
    }

    @Test
    fun magnifyMultipliesWithinRange() {
        assertEquals(MagnifyStep(3f, null), ZoomGate.magnify(2f, 1.5f, locked = false))
    }

    @Test
    fun magnifyClampsAndReportsTheLimit() {
        assertEquals(MagnifyStep(5f, ZoomLimit.MAX), ZoomGate.magnify(4f, 2f, locked = false))
        assertEquals(MagnifyStep(1f, ZoomLimit.MIN), ZoomGate.magnify(1.2f, 0.5f, locked = false))
    }

    @Test
    fun zoomingOutAtFitIsNotALimitMessage() {
        // Pinching in on an unzoomed page is normal; only text size reports a minimum.
        assertEquals(MagnifyStep(1f, null), ZoomGate.magnify(1f, 0.8f, locked = false))
    }

    @Test
    fun doubleTapTogglesBetweenFitAndTwoAndAHalf() {
        assertEquals(2.5f, ZoomGate.toggleDoubleTap(1f, locked = false))
        assertEquals(1f, ZoomGate.toggleDoubleTap(3f, locked = false))
    }

    @Test
    fun textSizeFollowsThePinchInFivePercentSteps() {
        assertEquals(TextSizeStep(125, null), ZoomGate.textSize(100, 1.23f, locked = false))
        assertEquals(TextSizeStep(80, null), ZoomGate.textSize(100, 0.79f, locked = false))
    }

    @Test
    fun textSizeReportsLimits() {
        assertEquals(TextSizeStep(300, ZoomLimit.MAX), ZoomGate.textSize(250, 1.5f, locked = false))
        assertEquals(TextSizeStep(50, ZoomLimit.MIN), ZoomGate.textSize(60, 0.5f, locked = false))
    }
}
