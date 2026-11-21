package app.folio.core.model

import app.folio.data.settings.NightLightMode
import app.folio.data.settings.NightLightSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightLightTest {

    @Test
    fun offModeIsNeverActive() {
        val settings = NightLightSettings(mode = NightLightMode.OFF)
        assertFalse(NightLight.isActive(settings, nowMinutes = 23 * 60))
    }

    @Test
    fun alwaysModeIsAlwaysActive() {
        val settings = NightLightSettings(mode = NightLightMode.ALWAYS)
        assertTrue(NightLight.isActive(settings, nowMinutes = 12 * 60))
    }

    @Test
    fun scheduleCrossingMidnightIsActiveOnBothSides() {
        val settings = NightLightSettings(mode = NightLightMode.SCHEDULED, startMinutes = 21 * 60, endMinutes = 7 * 60)
        assertTrue(NightLight.isActive(settings, nowMinutes = 23 * 60)) // 11pm
        assertTrue(NightLight.isActive(settings, nowMinutes = 1 * 60)) // 1am
        assertTrue(NightLight.isActive(settings, nowMinutes = 6 * 60 + 59))
        assertFalse(NightLight.isActive(settings, nowMinutes = 7 * 60)) // exactly end, exclusive
        assertFalse(NightLight.isActive(settings, nowMinutes = 12 * 60)) // noon
    }

    @Test
    fun scheduleWithinTheSameDay() {
        val settings = NightLightSettings(mode = NightLightMode.SCHEDULED, startMinutes = 9 * 60, endMinutes = 17 * 60)
        assertTrue(NightLight.isActive(settings, nowMinutes = 12 * 60))
        assertFalse(NightLight.isActive(settings, nowMinutes = 8 * 60))
        assertFalse(NightLight.isActive(settings, nowMinutes = 18 * 60))
    }

    @Test
    fun tintAndDimAreZeroWhenInactive() {
        val settings = NightLightSettings(mode = NightLightMode.OFF, warmth = 0.7f, dim = 0.3f)
        assertEquals(0f, NightLight.tintAlpha(settings, 23 * 60), 0f)
        assertEquals(0f, NightLight.dimAlpha(settings, 23 * 60), 0f)
    }

    @Test
    fun tintAndDimMatchSettingsWhenActive() {
        val settings = NightLightSettings(mode = NightLightMode.ALWAYS, warmth = 0.7f, dim = 0.3f)
        assertEquals(0.7f, NightLight.tintAlpha(settings, 23 * 60), 0f)
        assertEquals(0.3f, NightLight.dimAlpha(settings, 23 * 60), 0f)
    }

    @Test
    fun bedtimeNotDueWhenReminderIsOff() {
        val settings = NightLightSettings(bedtimeReminder = false, bedtimeMinutes = 23 * 60)
        assertFalse(NightLight.bedtimeDue(settings, 23 * 60 + 30))
    }

    @Test
    fun bedtimeDueWithinWindow() {
        val settings = NightLightSettings(bedtimeReminder = true, bedtimeMinutes = 23 * 60)
        assertTrue(NightLight.bedtimeDue(settings, nowMinutes = 23 * 60)) // right at bedtime
        assertTrue(NightLight.bedtimeDue(settings, nowMinutes = 23 * 60 + 179))
        assertFalse(NightLight.bedtimeDue(settings, nowMinutes = 23 * 60 + 180)) // window is exclusive
    }

    @Test
    fun bedtimeWindowWrapsPastMidnight() {
        val settings = NightLightSettings(bedtimeReminder = true, bedtimeMinutes = 23 * 60)
        assertTrue(NightLight.bedtimeDue(settings, nowMinutes = 60)) // 1am, 2 hours after 11pm
        assertFalse(NightLight.bedtimeDue(settings, nowMinutes = 22 * 60)) // before bedtime
    }
}
