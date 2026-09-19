package app.folio.reader.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.folio.core.model.InkPoint
import app.folio.core.model.InkStroke
import app.folio.core.model.InkStyle
import app.folio.core.model.InkTool
import app.folio.core.model.NormRect
import app.folio.core.model.PageFrame
import app.folio.reader.api.ScribbleState

private val ERASER_RADIUS = 12.dp

/**
 * Saved ink plus the stroke being drawn, over a page box whose shape matches the (cropped) page.
 * While [ScribbleState.active], one finger draws or erases; a second finger abandons the stroke
 * so the parent's pinch and two-finger pan take over.
 */
@Composable
fun InkLayer(
    strokes: List<InkStroke>,
    scribble: ScribbleState,
    crop: NormRect?,
    multiplyHighlighter: Boolean,
    onStroke: (points: List<InkPoint>, heightOverWidth: Float) -> Unit,
    onErase: (point: InkPoint, radius: Float, heightOverWidth: Float) -> Unit,
    onEraseEnd: () -> Unit,
    modifier: Modifier = Modifier,
    // PDF night mode color-inverts the page bitmap but not the ink layer above it; without this,
    // the default near-black pen becomes invisible against an inverted-to-white page. Only PEN
    // strokes are affected — highlighter and eraser are unaffected by night mode.
    invertPen: Boolean = false,
) {
    val live = remember { mutableStateListOf<InkPoint>() }
    val currentScribble by rememberUpdatedState(scribble)
    val currentCrop by rememberUpdatedState(crop)
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentOnErase by rememberUpdatedState(onErase)
    val currentOnEraseEnd by rememberUpdatedState(onEraseEnd)
    val eraserRadiusPx = with(LocalDensity.current) { ERASER_RADIUS.toPx() }

    val input = if (!scribble.active) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tool = currentScribble.tool
                val frame = currentCrop
                val width = size.width.toFloat().coerceAtLeast(1f)
                val height = size.height.toFloat().coerceAtLeast(1f)
                val heightOverWidth = PageFrame.heightOverWidth(width, height, frame)
                val radius = eraserRadiusPx * (frame?.width ?: 1f) / width
                fun toPage(position: Offset) = PageFrame.boxToPage(
                    InkPoint((position.x / width).coerceIn(0f, 1f), (position.y / height).coerceIn(0f, 1f)),
                    frame,
                )

                down.consume()
                live.clear()
                // Tracks whether this eraser gesture's currentOnEraseEnd() has run yet, so it
                // fires exactly once whether the gesture ends normally, is abandoned, or is
                // cancelled outright (e.g. drawing mode switched off mid-erase) — otherwise the
                // next eraser gesture would merge into the same undo step.
                var eraseEnded = false
                fun endEraseOnce() {
                    if (tool == InkTool.ERASER && !eraseEnded) {
                        eraseEnded = true
                        currentOnEraseEnd()
                    }
                }
                try {
                    if (tool == InkTool.ERASER) {
                        currentOnErase(toPage(down.position), radius, heightOverWidth)
                    } else {
                        live += toPage(down.position)
                    }
                    var abandoned = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) {
                            abandoned = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        // A second finger landing outside our bounds (e.g. the letterboxed margin
                        // around a fit-page PDF) never reaches us as its own pointer: the ancestor's
                        // pinch/pan gesture sees it first and consumes our finger's changes in the
                        // Initial pass, since single-finger pan is off while drawing. A consumed
                        // change is the only signal we get that the pinch has taken over.
                        if (change.isConsumed) {
                            abandoned = true
                            break
                        }
                        if (!change.pressed) break
                        if (change.positionChanged()) {
                            change.consume()
                            val point = toPage(change.position)
                            if (tool == InkTool.ERASER) currentOnErase(point, radius, heightOverWidth) else live += point
                        }
                    }
                    if (!abandoned && tool != InkTool.ERASER && live.isNotEmpty()) {
                        currentOnStroke(live.toList(), heightOverWidth)
                    }
                } finally {
                    endEraseOnce()
                    live.clear()
                }
            }
        }
    }

    Canvas(modifier.fillMaxSize().clipToBounds().then(input)) {
        strokes.forEach { drawInk(it.tool, it.argb, it.widthNorm, it.points, crop, multiplyHighlighter, invertPen) }
        if (live.isNotEmpty()) {
            val tool = scribble.tool
            drawInk(
                tool = tool,
                argb = InkStyle.strokeArgb(scribble.style),
                widthNorm = InkStyle.widthNorm(tool, scribble.style.widthIndex),
                points = live,
                crop = crop,
                multiplyHighlighter = multiplyHighlighter,
                invertPen = invertPen,
            )
        }
    }
}

/** Read-only ink, e.g. a drawn note's thumbnail. */
@Composable
fun InkPreview(strokes: List<InkStroke>, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().clipToBounds()) {
        strokes.forEach { drawInk(it.tool, it.argb, it.widthNorm, it.points, null, multiplyHighlighter = true) }
    }
}

fun DrawScope.drawInk(
    tool: InkTool,
    argb: Int,
    widthNorm: Float,
    points: List<InkPoint>,
    crop: NormRect?,
    multiplyHighlighter: Boolean,
    // See InkLayer's invertPen doc: only PEN strokes are recolored, so ink stays visible when the
    // page bitmap underneath it has been color-inverted for night mode.
    invertPen: Boolean = false,
) {
    if (points.isEmpty()) return
    val pageWidthPx = size.width / (crop?.width?.takeIf { it > 0f } ?: 1f)
    val strokeWidth = (widthNorm * pageWidthPx).coerceAtLeast(1f)
    val base = Color(argb)
    val color = if (invertPen && tool == InkTool.PEN) {
        base.copy(red = 1f - base.red, green = 1f - base.green, blue = 1f - base.blue)
    } else {
        base
    }
    val highlighter = tool == InkTool.HIGHLIGHTER
    // Multiply keeps the text under a highlighter readable; on inverted night pages it would vanish.
    val blend = if (highlighter && multiplyHighlighter) BlendMode.Multiply else BlendMode.SrcOver

    fun onScreen(point: InkPoint): Offset {
        val box = PageFrame.pageToBox(point, crop)
        return Offset(box.x * size.width, box.y * size.height)
    }

    if (points.size == 1) {
        drawCircle(color, radius = strokeWidth / 2f, center = onScreen(points[0]), blendMode = blend)
        return
    }
    val path = Path().apply {
        val first = onScreen(points[0])
        moveTo(first.x, first.y)
        for (i in 1 until points.size) {
            val next = onScreen(points[i])
            lineTo(next.x, next.y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = if (highlighter) StrokeCap.Square else StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
        blendMode = blend,
    )
}
