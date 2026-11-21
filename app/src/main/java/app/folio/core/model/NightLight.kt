package app.folio.core.model

import app.folio.data.settings.NightLightMode
import app.folio.data.settings.NightLightSettings

/** Pure scheduling/tint logic for the night light and bedtime nudge, kept out of Compose. */
object NightLight {

    /** The bedtime card is offered for this long after bedtime, in minutes. */
    const val BEDTIME_WINDOW = 180

    /**
     * Whether the night light should be tinting the screen right now, given the time of day in
     * minutes since midnight (0..1439). Handles a schedule that crosses midnight, e.g. 21:00 to
     * 07:00.
     */
    fun isActive(settings: NightLightSettings, nowMinutes: Int): Boolean = when (settings.mode) {
        NightLightMode.OFF -> false
        NightLightMode.ALWAYS -> true
        NightLightMode.SCHEDULED -> inWindow(settings.startMinutes, settings.endMinutes, nowMinutes)
    }

    /** True if [nowMinutes] falls in [start, end), wrapping past midnight when end < start. */
    fun inWindow(start: Int, end: Int, nowMinutes: Int): Boolean {
        val s = start.mod(1440)
        val e = end.mod(1440)
        val now = nowMinutes.mod(1440)
        return if (s == e) {
            true
        } else if (s < e) {
            now in s until e
        } else {
            now >= s || now < e
        }
    }

    /** 0f (no tint) to 1f (full warmth), only when the light is active. */
    fun tintAlpha(settings: NightLightSettings, nowMinutes: Int): Float =
        if (isActive(settings, nowMinutes)) settings.warmth.coerceIn(0f, 1f) else 0f

    /** 0f (no dimming) to 1f, only when the light is active. */
    fun dimAlpha(settings: NightLightSettings, nowMinutes: Int): Float =
        if (isActive(settings, nowMinutes)) settings.dim.coerceIn(0f, 1f) else 0f

    /**
     * Whether the bedtime card should show: bedtime reminders are on, and [nowMinutes] is within
     * [BEDTIME_WINDOW] minutes after [NightLightSettings.bedtimeMinutes] (wrapping past midnight).
     */
    fun bedtimeDue(settings: NightLightSettings, nowMinutes: Int): Boolean {
        if (!settings.bedtimeReminder) return false
        val bedtime = settings.bedtimeMinutes.mod(1440)
        val now = nowMinutes.mod(1440)
        val minutesSinceBedtime = (now - bedtime).mod(1440)
        return minutesSinceBedtime in 0 until BEDTIME_WINDOW
    }
}
