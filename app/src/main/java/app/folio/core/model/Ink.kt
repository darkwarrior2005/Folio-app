package app.folio.core.model

import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class InkTool { PEN, HIGHLIGHTER, ERASER }

/** A point in whole-page coordinates: 0..1 across the page's width and height. */
data class InkPoint(val x: Float, val y: Float)

/** A normalized rectangle, such as the part of a page left after margin cropping. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A saved stroke, decoded for drawing and hit testing. [argb] carries the opacity in its alpha. */
data class InkStroke(
    val id: Long,
    val tool: InkTool,
    val argb: Int,
    val widthNorm: Float,
    val points: List<InkPoint>,
)

/** What an inking tool draws with; remembered per tool. */
@Serializable
data class InkToolStyle(val argb: Long, val widthIndex: Int, val opacityPercent: Int)

/** Maps between the box a page is drawn in (possibly cropped) and whole-page coordinates. */
object PageFrame {
    fun boxToPage(point: InkPoint, crop: NormRect?): InkPoint {
        val c = crop ?: return point
        return InkPoint(c.left + point.x * c.width, c.top + point.y * c.height)
    }

    fun pageToBox(point: InkPoint, crop: NormRect?): InkPoint {
        val c = crop ?: return point
        if (c.width <= 0f || c.height <= 0f) return point
        return InkPoint((point.x - c.left) / c.width, (point.y - c.top) / c.height)
    }

    /** The whole page's height in page widths, from the size of the (cropped) box on screen. */
    fun heightOverWidth(boxWidth: Float, boxHeight: Float, crop: NormRect?): Float {
        val cw = crop?.width?.takeIf { it > 0f } ?: 1f
        val ch = crop?.height?.takeIf { it > 0f } ?: 1f
        if (boxWidth <= 0f) return 1f
        return (boxHeight / ch) / (boxWidth / cw)
    }
}

/** Points are stored as little-endian float pairs, 8 bytes per point. */
object InkPacking {
    fun pack(points: List<InkPoint>): ByteArray {
        val buffer = ByteBuffer.allocate(points.size * 8).order(ByteOrder.LITTLE_ENDIAN)
        points.forEach { point ->
            buffer.putFloat(point.x)
            buffer.putFloat(point.y)
        }
        return buffer.array()
    }

    fun unpack(bytes: ByteArray): List<InkPoint> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return List(bytes.size / 8) {
            val x = buffer.float
            val y = buffer.float
            InkPoint(x, y)
        }
    }
}

/** Ramer–Douglas–Peucker, measured in page widths so tall pages are not simplified unevenly. */
object StrokeSimplifier {
    const val EPSILON = 0.0015f

    fun simplify(points: List<InkPoint>, epsilon: Float = EPSILON, heightOverWidth: Float = 1f): List<InkPoint> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true
        val ranges = ArrayDeque<Pair<Int, Int>>()
        ranges.addLast(0 to points.lastIndex)
        while (ranges.isNotEmpty()) {
            val (start, end) = ranges.removeLast()
            var farthest = -1
            var farthestDistance = 0f
            for (i in start + 1 until end) {
                val distance = segmentDistance(points[i], points[start], points[end], heightOverWidth)
                if (distance > farthestDistance) {
                    farthestDistance = distance
                    farthest = i
                }
            }
            if (farthest >= 0 && farthestDistance > epsilon) {
                keep[farthest] = true
                ranges.addLast(start to farthest)
                ranges.addLast(farthest to end)
            }
        }
        return points.filterIndexed { index, _ -> keep[index] }
    }
}

object StrokeHit {
    /** True when a round eraser of [radius] at [touch] overlaps the stroke's inked area. */
    fun hits(points: List<InkPoint>, widthNorm: Float, touch: InkPoint, radius: Float, heightOverWidth: Float): Boolean {
        val reach = radius + widthNorm / 2f
        if (points.size == 1) return segmentDistance(touch, points[0], points[0], heightOverWidth) <= reach
        return points.zipWithNext().any { (a, b) -> segmentDistance(touch, a, b, heightOverWidth) <= reach }
    }
}

/** Distance from [p] to the segment [a]–[b], in page widths. */
internal fun segmentDistance(p: InkPoint, a: InkPoint, b: InkPoint, heightOverWidth: Float): Float {
    val ay = a.y * heightOverWidth
    val by = b.y * heightOverWidth
    val py = p.y * heightOverWidth
    val dx = b.x - a.x
    val dy = by - ay
    val lengthSquared = dx * dx + dy * dy
    val t = if (lengthSquared == 0f) 0f else (((p.x - a.x) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
    val cx = a.x + t * dx - p.x
    val cy = ay + t * dy - py
    return sqrt(cx * cx + cy * cy)
}

object InkStyle {
    /** Ink, red, orange, yellow, green, blue, purple, white. */
    val PALETTE: List<Long> = listOf(
        0xFF1F1F1F, 0xFFE53935, 0xFFFB8C00, 0xFFFDD835,
        0xFF43A047, 0xFF1E88E5, 0xFF8E24AA, 0xFFFFFFFF,
    )

    const val MIN_OPACITY = 10
    const val MAX_OPACITY = 100
    const val OPACITY_STEP = 5

    /** Drawn notes are cards 1.4 times as tall as they are wide. */
    const val DRAWN_NOTE_ASPECT = 1.4f

    val PEN_DEFAULT = InkToolStyle(argb = 0xFF1F1F1F, widthIndex = 1, opacityPercent = 100)
    val HIGHLIGHTER_DEFAULT = InkToolStyle(argb = 0xFFFDD835, widthIndex = 1, opacityPercent = 40)

    private val PEN_WIDTHS = listOf(0.003f, 0.006f, 0.012f)
    private val HIGHLIGHTER_WIDTHS = listOf(0.015f, 0.025f, 0.04f)

    fun widthNorm(tool: InkTool, index: Int): Float {
        val widths = if (tool == InkTool.HIGHLIGHTER) HIGHLIGHTER_WIDTHS else PEN_WIDTHS
        return widths[index.coerceIn(0, widths.lastIndex)]
    }

    fun snapOpacity(percent: Float): Int =
        ((percent / OPACITY_STEP).roundToInt() * OPACITY_STEP).coerceIn(MIN_OPACITY, MAX_OPACITY)

    fun strokeArgb(style: InkToolStyle): Int {
        val opacity = style.opacityPercent.coerceIn(MIN_OPACITY, MAX_OPACITY)
        val alpha = (opacity * 255 / 100f).roundToInt()
        return (style.argb.toInt() and 0x00FFFFFF) or (alpha shl 24)
    }

    fun opacityPercentOf(argb: Int): Int = ((argb ushr 24) * 100f / 255f).roundToInt()
}

class UndoStack<T>(private val limit: Int = 100) {
    private val undoEdits = ArrayDeque<T>()
    private val redoEdits = ArrayDeque<T>()

    val canUndo: Boolean get() = undoEdits.isNotEmpty()
    val canRedo: Boolean get() = redoEdits.isNotEmpty()

    fun push(edit: T) {
        undoEdits.addLast(edit)
        if (undoEdits.size > limit) undoEdits.removeFirst()
        redoEdits.clear()
    }

    fun undo(): T? = undoEdits.removeLastOrNull()?.also { redoEdits.addLast(it) }

    fun redo(): T? = redoEdits.removeLastOrNull()?.also { undoEdits.addLast(it) }

    fun clear() {
        undoEdits.clear()
        redoEdits.clear()
    }
}

/** Drawn notes shown as "here" while reading a reflowable book. */
object DrawnNoteProximity {
    const val WINDOW = 0.01f

    fun <T> near(items: List<T>, current: Float, progress: (T) -> Float): List<T> =
        items.filter { abs(progress(it) - current) <= WINDOW + 1e-6f }
}
