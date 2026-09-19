package app.folio.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Drag-to-reorder for short, uniform lists (the reading queue, books inside a collection).
 * Rows are a fixed height, which keeps the maths honest and the drag predictable.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    rowHeight: Dp = 72.dp,
    onMove: (from: Int, to: Int) -> Unit,
    content: @Composable (item: T, index: Int, dragHandle: Modifier) -> Unit,
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            val isDragging = index == draggingIndex
            val targetIndex = if (draggingIndex >= 0) {
                (draggingIndex + (dragOffset / rowHeightPx).roundToInt())
                    .coerceIn(0, items.lastIndex)
            } else {
                -1
            }
            // Items between the source and target slide out of the way.
            val shift = when {
                draggingIndex < 0 || isDragging -> 0f
                draggingIndex < targetIndex && index in (draggingIndex + 1)..targetIndex -> -rowHeightPx
                draggingIndex > targetIndex && index in targetIndex until draggingIndex -> rowHeightPx
                else -> 0f
            }

            val rowModifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    translationY = if (isDragging) dragOffset else shift
                }
                .then(if (isDragging) Modifier.shadow(6.dp) else Modifier)

            val handle = Modifier.pointerInput(items.size, index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        draggingIndex = index
                        dragOffset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                    },
                    onDragEnd = {
                        val from = draggingIndex
                        val to = (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, items.lastIndex)
                        draggingIndex = -1
                        dragOffset = 0f
                        if (from >= 0 && from != to) onMove(from, to)
                    },
                    onDragCancel = {
                        draggingIndex = -1
                        dragOffset = 0f
                    },
                )
            }

            androidx.compose.foundation.layout.Box(rowModifier) {
                content(item, index, handle)
            }
        }
    }
}

fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from == to || from !in indices || to !in indices) return this
    val mutable = toMutableList()
    val item = mutable.removeAt(from)
    mutable.add(to, item)
    return mutable
}
