package app.folio.core.model

/** When the warm night filter is on. */
enum class NightLightMode { OFF, ALWAYS, SCHEDULED }

/**
 * The warm night filter: an amber tint laid over all of Folio to cut the blue light that keeps
 * people awake, with optional extra dimming below the phone's lowest brightness.
 */
object NightLight {

    /**
     * Whether the filter is on at [nowMinutes] (minutes since midnight). A schedule may cross
     * midnight, e.g. 21:00 to 07:00; a start equal to the end means all day.
     */
    fun isActive(mode: NightLightMode, nowMinutes: Int, startMinutes: Int, endMinutes: Int): Boolean = when (mode) {
        NightLightMode.OFF -> false
        NightLightMode.ALWAYS -> true
        NightLightMode.SCHEDULED -> when {
            startMinutes == endMinutes -> true
            startMinutes < endMinutes -> nowMinutes in startMinutes until endMinutes
            else -> nowMinutes >= startMinutes || nowMinutes < endMinutes
        }
    }

    /** Opacity of the amber tint for a warmth of 0..1; kept well short of opaque so text stays readable. */
    fun tintAlpha(warmth: Float): Float = warmth.coerceIn(0f, 1f) * MAX_TINT

    /** Opacity of the extra darkening for a dimming of 0..1. */
    fun dimAlpha(dim: Float): Float = dim.coerceIn(0f, 1f) * MAX_DIM

    /**
     * Whether the bedtime nudge is due at [nowMinutes]: from bedtime until three hours after it,
     * crossing midnight when it has to. Past that it is morning reading, not a late night.
     */
    fun bedtimeDue(nowMinutes: Int, bedtimeMinutes: Int): Boolean {
        val since = ((nowMinutes - bedtimeMinutes) % MINUTES_PER_DAY + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return since < BEDTIME_WINDOW
    }

    /** "21:00"-style clock text for minutes since midnight. */
    fun clock(minutes: Int): String {
        val m = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return "%02d:%02d".format(m / 60, m % 60)
    }

    const val MINUTES_PER_DAY = 24 * 60
    const val BEDTIME_WINDOW = 3 * 60
    private const val MAX_TINT = 0.4f
    private const val MAX_DIM = 0.6f
}
