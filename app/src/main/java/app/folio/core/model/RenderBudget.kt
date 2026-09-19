package app.folio.core.model

import kotlin.math.sqrt

/** Keeps a zoomed page bitmap within memory so zooming degrades to a message instead of a crash. */
object RenderBudget {
    const val BYTES_PER_PIXEL = 4

    /**
     * Largest zoom <= [requested] whose bitmap (viewport width x zoom, height from [pageAspect])
     * fits in [budgetBytes]. Never returns less than 1 (fit), which is always rendered.
     */
    fun maxZoom(viewportWidthPx: Int, pageAspect: Float, budgetBytes: Long, requested: Float): Float {
        if (viewportWidthPx <= 0 || pageAspect <= 0f) return 1f
        val fitWidth = viewportWidthPx.toDouble()
        val fitBytes = fitWidth * (fitWidth / pageAspect) * BYTES_PER_PIXEL
        // bytes grow with zoom squared
        val affordable = sqrt(budgetBytes / fitBytes).toFloat()
        return requested.coerceAtMost(affordable).coerceAtLeast(1f)
    }

    fun defaultBudget(maxHeapBytes: Long): Long = maxHeapBytes / 8
}
