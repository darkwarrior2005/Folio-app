package app.folio.ui.screens.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

/**
 * Dims all but an 84dp band, so attention stays on one line or two at a time. Only the amber
 * handle takes touches (vertical drag to move the band) - nothing else here adds pointer input,
 * so the page underneath still turns and selects normally.
 */
@Composable
fun ReadingRuler(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val bandHeightPx = with(density) { 84.dp.toPx() }
    var bandTopPx by remember { mutableFloatStateOf(300f) }

    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val top = bandTopPx.coerceIn(0f, (size.height - bandHeightPx).coerceAtLeast(0f))
            drawRect(color = Color.Black.copy(alpha = 0.55f), size = Size(size.width, top))
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, top + bandHeightPx),
                size = Size(size.width, (size.height - top - bandHeightPx).coerceAtLeast(0f)),
            )
        }

        val handleOffsetY = (bandTopPx + bandHeightPx / 2 - with(density) { 4.dp.toPx() }).toInt()
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .offset { IntOffset(0, handleOffsetY) }
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFFB300))
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount -> bandTopPx += dragAmount }
                },
        )
    }
}
