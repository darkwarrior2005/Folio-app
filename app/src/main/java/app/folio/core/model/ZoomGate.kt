package app.folio.core.model

import kotlin.math.roundToInt

enum class ZoomLimit { MIN, MAX }

data class MagnifyStep(val zoom: Float, val hitLimit: ZoomLimit?)

data class TextSizeStep(val percent: Int, val hitLimit: ZoomLimit?)

/**
 * Every reader asks this before changing zoom, so the zoom lock cannot be bypassed by one format.
 * A null result means "do nothing and say nothing": the lock is on.
 */
object ZoomGate {
    const val MIN_ZOOM = 1f
    const val MAX_ZOOM = 5f
    const val DOUBLE_TAP_ZOOM = 2.5f
    const val MIN_TEXT = 50
    const val MAX_TEXT = 300
    const val TEXT_STEP = 5

    fun magnify(current: Float, factor: Float, locked: Boolean): MagnifyStep? {
        if (locked) return null
        val wanted = current * factor
        return when {
            wanted > MAX_ZOOM -> MagnifyStep(MAX_ZOOM, ZoomLimit.MAX)
            wanted < MIN_ZOOM -> MagnifyStep(MIN_ZOOM, if (current > MIN_ZOOM) ZoomLimit.MIN else null)
            else -> MagnifyStep(wanted, null)
        }
    }

    fun toggleDoubleTap(current: Float, locked: Boolean): Float? {
        if (locked) return null
        return if (current > MIN_ZOOM) MIN_ZOOM else DOUBLE_TAP_ZOOM
    }

    fun textSize(startPercent: Int, cumulativeFactor: Float, locked: Boolean): TextSizeStep? {
        if (locked) return null
        val raw = startPercent * cumulativeFactor
        val stepped = (raw / TEXT_STEP).roundToInt() * TEXT_STEP
        return when {
            stepped > MAX_TEXT -> TextSizeStep(MAX_TEXT, ZoomLimit.MAX)
            stepped < MIN_TEXT -> TextSizeStep(MIN_TEXT, ZoomLimit.MIN)
            else -> TextSizeStep(stepped, null)
        }
    }
}
