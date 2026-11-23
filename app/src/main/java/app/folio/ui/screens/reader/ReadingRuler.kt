package app.folio.ui.screens.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.folio.R
import kotlin.math.roundToInt

/**
 * A reading ruler: everything but a band of a few lines is softly dimmed, so the eye keeps its
 * line. Only the small handle on the band's edge takes touches; the page under it still turns and
 * selects as usual. Drag the handle to move the band.
 */
@Composable
fun ReadingRuler(modifier: Modifier = Modifier) {
    // Where the band's centre sits, as a fraction of the screen height.
    var centre by rememberSaveable { mutableFloatStateOf(0.45f) }
    val bandHeight = 84.dp
    val handle = 40.dp
    val description = stringResource(R.string.reading_ruler_handle)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val heightPx = constraints.maxHeight.toFloat()
        val bandPx = with(density) { bandHeight.toPx() }
        val handlePx = with(density) { handle.toPx() }

        Canvas(Modifier.fillMaxSize()) {
            val shade = Color.Black.copy(alpha = 0.42f)
            val top = centre * size.height - bandPx / 2f
            val bottom = top + bandPx
            if (top > 0f) drawRect(shade, Offset.Zero, Size(size.width, top))
            if (bottom < size.height) drawRect(shade, Offset(0f, bottom), Size(size.width, size.height - bottom))
            // A warm line under the band marks the line being read.
            drawRect(Color(0x66E8B04A), Offset(0f, bottom - 3f), Size(size.width, 3f))
        }

        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, (centre * heightPx - handlePx / 2f).roundToInt()) }
                .padding(end = 6.dp)
                .size(width = 22.dp, height = handle)
                .clip(RoundedCornerShape(11.dp))
                .background(Color(0xCCE8B04A))
                .semantics { contentDescription = description }
                .pointerInput(heightPx) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (heightPx > 0f) {
                            centre = (centre + dragAmount / heightPx).coerceIn(0.05f, 0.95f)
                        }
                    }
                },
        )
    }
}
