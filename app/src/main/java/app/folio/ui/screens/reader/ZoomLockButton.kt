package app.folio.ui.screens.reader

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.core.model.NormalizedOffset
import app.folio.core.model.ZoomLockPlacement
import kotlin.math.roundToInt

private val BUTTON_SIZE = 44.dp
private val EDGE_GAP = 8.dp

/**
 * Translucent, draggable zoom lock. Tap toggles; long-press then drag moves it, and on release it
 * snaps to the nearer side edge. Locking is silent by design: only the icon changes.
 */
@Composable
fun ZoomLockButton(
    locked: Boolean,
    offset: NormalizedOffset,
    onToggle: () -> Unit,
    onMoved: (NormalizedOffset) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val sizePx = with(density) { BUTTON_SIZE.toPx() }
        val gapPx = with(density) { EDGE_GAP.toPx() }
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        fun toPx(value: NormalizedOffset) = Offset(
            x = gapPx + value.x * (widthPx - sizePx - 2 * gapPx),
            y = value.y * heightPx - sizePx / 2,
        )

        var dragPosition by remember { mutableStateOf<Offset?>(null) }
        var pressed by remember { mutableStateOf(false) }
        val position = dragPosition ?: toPx(offset)
        val alpha by animateFloatAsState(if (pressed || dragPosition != null) 1f else 0.4f, label = "zoomLockAlpha")
        val label = stringResource(if (locked) R.string.zoom_unlock else R.string.zoom_lock)

        Box(
            Modifier
                .offset { IntOffset(position.x.roundToInt(), position.y.roundToInt()) }
                .size(BUTTON_SIZE)
                .alpha(alpha)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = label }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            tryAwaitRelease()
                            pressed = false
                        },
                        onTap = { onToggle() },
                    )
                }
                .pointerInput(widthPx, heightPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragPosition = toPx(offset) },
                        onDrag = { change, amount ->
                            change.consume()
                            dragPosition = (dragPosition ?: toPx(offset)) + amount
                        },
                        onDragEnd = {
                            val end = dragPosition ?: return@detectDragGesturesAfterLongPress
                            val xFraction = (end.x + sizePx / 2) / widthPx
                            val yFraction = (end.y + sizePx / 2) / heightPx
                            onMoved(ZoomLockPlacement.snap(xFraction, yFraction))
                            dragPosition = null
                        },
                        onDragCancel = { dragPosition = null },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                contentDescription = null,
                tint = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
