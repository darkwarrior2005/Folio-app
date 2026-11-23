package app.folio.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.folio.core.model.NightLight
import app.folio.core.model.NightLightMode
import app.folio.data.settings.NightLightSettings
import kotlinx.coroutines.delay
import java.util.Calendar

/** Warm amber that takes the blue out of the screen without turning it orange. */
private val NightAmber = Color(0xFFFF9A3C)

/**
 * Lays the warm night filter over [content]: an amber tint and, if asked, extra dimming. It only
 * draws, so every touch still reaches the app beneath. The filter eases in and out over a few
 * seconds when the schedule starts or ends, rather than switching abruptly.
 */
@Composable
fun NightLightOverlay(settings: NightLightSettings, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val nowMinutes by produceState(minutesNow(), settings.mode) {
        // Only a schedule needs the clock; re-checked each minute, at the top of the minute.
        while (settings.mode == NightLightMode.SCHEDULED) {
            value = minutesNow()
            delay((60 - Calendar.getInstance().get(Calendar.SECOND)) * 1_000L)
        }
    }
    val active = NightLight.isActive(settings.mode, nowMinutes, settings.startMinutes, settings.endMinutes)
    val spec = if (reduceMotion) snap<Float>() else tween(durationMillis = 1_500)
    val tint by animateFloatAsState(if (active) NightLight.tintAlpha(settings.warmth) else 0f, spec, label = "nightTint")
    val dim by animateFloatAsState(if (active) NightLight.dimAlpha(settings.dim) else 0f, spec, label = "nightDim")

    Box(Modifier.fillMaxSize()) {
        content()
        if (tint > 0f || dim > 0f) {
            Canvas(Modifier.fillMaxSize()) {
                if (tint > 0f) drawRect(NightAmber.copy(alpha = tint))
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }
        }
    }
}

private fun minutesNow(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
