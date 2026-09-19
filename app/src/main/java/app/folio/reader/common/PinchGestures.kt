package app.folio.reader.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs

/**
 * Two-finger pinch that wins over child views. Events are read in the Initial pass (before
 * children) and consumed once a second finger is down, which makes an embedded WebView receive a
 * cancel instead of its own pinch or scroll.
 */
fun Modifier.pinchGesture(
    key: Any?,
    onStart: () -> Unit,
    onPinch: (cumulativeScale: Float) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var startSpan = 0f
        var pinching = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val span = (pressed[0].position - pressed[1].position).getDistance()
                if (!pinching) {
                    pinching = true
                    startSpan = span.coerceAtLeast(1f)
                    onStart()
                } else {
                    onPinch(span / startSpan)
                }
                event.changes.forEach { it.consume() }
            } else if (pinching) {
                // One finger lifted: keep swallowing until all are up so no stray scroll happens.
                event.changes.forEach { it.consume() }
            }
        }
        if (pinching) onEnd()
    }
}

/** Pinch-zoom and pan for bitmap readers; one-finger pan is only taken while zoomed in and not drawing. */
fun Modifier.magnifyGestures(
    key: Any?,
    zoom: () -> Float,
    onZoom: (factor: Float, centroid: Offset) -> Unit,
    onPan: (delta: Offset) -> Unit,
    onEnd: () -> Unit,
    /** False while drawing: one finger is the pen, only two fingers pan. */
    singleFingerPan: () -> Boolean = { true },
): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var lastSpan = 0f
        var lastCentroid: Offset? = null
        var active = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            val centroid = pressed.fold(Offset.Zero) { acc, c -> acc + c.position } / pressed.size.toFloat()
            if (pressed.size >= 2) {
                val span = (pressed[0].position - pressed[1].position).getDistance()
                if (lastSpan > 0f && abs(span - lastSpan) > 0.5f) onZoom(span / lastSpan, centroid)
                lastCentroid?.let { onPan(centroid - it) }
                lastSpan = span
                active = true
                event.changes.forEach { it.consume() }
            } else if (singleFingerPan() && zoom() > 1f && pressed.first().positionChanged()) {
                lastCentroid?.let { onPan(centroid - it) }
                lastSpan = 0f
                active = true
                event.changes.forEach { it.consume() }
            } else {
                lastSpan = 0f
            }
            lastCentroid = centroid
        }
        if (active) onEnd()
    }
}
