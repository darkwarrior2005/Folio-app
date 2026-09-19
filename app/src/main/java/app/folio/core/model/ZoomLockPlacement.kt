package app.folio.core.model

import kotlinx.serialization.Serializable

/** A point as fractions of the screen, so a position survives rotation and different screens. */
@Serializable
data class NormalizedOffset(val x: Float, val y: Float)

object ZoomLockPlacement {
    const val MIN_Y = 0.12f
    const val MAX_Y = 0.88f
    val DEFAULT = NormalizedOffset(1f, 0.62f)

    /** The button rests against the nearer side edge, away from the top and bottom toolbars. */
    fun snap(x: Float, y: Float): NormalizedOffset =
        NormalizedOffset(if (x < 0.5f) 0f else 1f, y.coerceIn(MIN_Y, MAX_Y))
}
