package app.folio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.folio.core.model.NightLight
import app.folio.data.settings.NightLightSettings
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Draws a full-screen overlay over the whole app: an amber layer for warmth, then a black layer
 * for extra dimming. Never takes touches, and fades unless reduce-motion is on.
 */
@Composable
fun NightLightOverlay(settings: NightLightSettings, reduceMotion: Boolean, content: @Composable () -> Unit) {
    var nowMinutes by remember { mutableIntStateOf(currentMinutesOfDay()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMinutes = currentMinutesOfDay()
        }
    }

    val targetTint = NightLight.tintAlpha(settings, nowMinutes)
    val targetDim = NightLight.dimAlpha(settings, nowMinutes)
    val tint by if (reduceMotion) {
        androidx.compose.runtime.remember(targetTint) { androidx.compose.runtime.mutableFloatStateOf(targetTint) }
    } else {
        animateFloatAsState(targetValue = targetTint, label = "nightLightTint")
    }
    val dim by if (reduceMotion) {
        androidx.compose.runtime.remember(targetDim) { androidx.compose.runtime.mutableFloatStateOf(targetDim) }
    } else {
        animateFloatAsState(targetValue = targetDim, label = "nightLightDim")
    }

    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        content()
        if (tint > 0f) {
            // A plain Canvas with no pointerInput never intercepts touches, so this never blocks
            // reading - it only draws.
            Canvas(Modifier.fillMaxSize()) {
                drawRect(color = Color(0xFFFFA000).copy(alpha = tint * 0.35f))
                if (dim > 0f) {
                    drawRect(color = Color.Black.copy(alpha = dim * 0.6f))
                }
            }
        }
    }
}

private fun currentMinutesOfDay(): Int {
    val calendar = Calendar.getInstance()
    return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}
