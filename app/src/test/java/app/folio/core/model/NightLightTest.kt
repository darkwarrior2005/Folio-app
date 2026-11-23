package app.folio.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLightTest {

    private fun at(h: Int, m: Int = 0) = h * 60 + m

    @Test
    fun `off and always ignore the clock`() {
        assertFalse(NightLight.isActive(NightLightMode.OFF, at(23), at(21), at(7)))
        assertTrue(NightLight.isActive(NightLightMode.ALWAYS, at(12), at(21), at(7)))
    }

    @Test
    fun `a schedule across midnight covers the night`() {
        val on = { h: Int, m: Int -> NightLight.isActive(NightLightMode.SCHEDULED, at(h, m), at(21), at(7)) }
        assertTrue(on(21, 0))
        assertTrue(on(23, 59))
        assertTrue(on(0, 0))
        assertTrue(on(6, 59))
        assertFalse(on(7, 0))
        assertFalse(on(20, 59))
    }

    @Test
    fun `a daytime schedule and an all-day one`() {
        assertTrue(NightLight.isActive(NightLightMode.SCHEDULED, at(14), at(13), at(15)))
        assertFalse(NightLight.isActive(NightLightMode.SCHEDULED, at(16), at(13), at(15)))
        assertTrue(NightLight.isActive(NightLightMode.SCHEDULED, at(3), at(22), at(22)))
    }

    @Test
    fun `strengths stay short of opaque and clocks read like clocks`() {
        assertEquals(0f, NightLight.tintAlpha(0f), 0f)
        assertTrue(NightLight.tintAlpha(1f) < 0.5f)
        assertTrue(NightLight.dimAlpha(5f) <= 0.6f)
        assertEquals("21:00", NightLight.clock(at(21)))
        assertEquals("07:05", NightLight.clock(at(7, 5)))
    }

    @Test
    fun `the bedtime nudge is due for three hours from bedtime, across midnight`() {
        assertTrue(NightLight.bedtimeDue(at(23), at(23)))
        assertTrue(NightLight.bedtimeDue(at(1, 30), at(23)))
        assertFalse(NightLight.bedtimeDue(at(2), at(23)))
        assertFalse(NightLight.bedtimeDue(at(22, 59), at(23)))
    }
}
