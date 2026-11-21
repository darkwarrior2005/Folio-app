package app.folio.ui.screens.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import kotlin.math.sin
import kotlin.random.Random

private const val SAMPLE_RATE = 22_050
private const val DURATION_SECONDS = 0.11

/**
 * A short page-turn tick: a synthesized 0.11s burst of filtered noise (no asset file), played
 * through an AudioTrack with usage ASSISTANCE_SONIFICATION so it's silenced by silent mode, plus
 * a KEYBOARD_RELEASE haptic (CLOCK_TICK before API 27). Call [PageTurnFeedback.play] from a
 * LaunchedEffect keyed on the current page, never on first open.
 */
class PageTurnFeedback {

    private var track: AudioTrack? = null

    fun play() {
        val audioTrack = track ?: build().also { track = it }
        audioTrack.stop()
        audioTrack.reloadStaticData()
        audioTrack.play()
    }

    fun release() {
        track?.release()
        track = null
    }

    private fun build(): AudioTrack {
        val sampleCount = (SAMPLE_RATE * DURATION_SECONDS).toInt()
        val samples = ShortArray(sampleCount)
        val random = Random(0)
        var filtered = 0f
        for (i in samples.indices) {
            val noise = random.nextFloat() * 2f - 1f
            // A simple one-pole low-pass so the burst sounds like a soft tick, not white noise.
            filtered = filtered * 0.7f + noise * 0.3f
            val envelope = 1f - (i.toFloat() / sampleCount)
            samples[i] = (filtered * envelope * Short.MAX_VALUE * 0.5f).toInt().toShort()
        }
        val bytes = java.nio.ByteBuffer.allocate(samples.size * 2)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .apply { samples.forEach { putShort(it) } }
            .array()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bytes.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audioTrack.write(bytes, 0, bytes.size)
        return audioTrack
    }
}

/** Fires sound/haptic feedback on every page change after the first, keyed on [page]. */
@Composable
fun PageTurnEffect(page: Int, soundEnabled: Boolean, hapticEnabled: Boolean) {
    val feedback = remember { PageTurnFeedback() }
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current

    DisposableEffect(Unit) { onDispose { feedback.release() } }

    LaunchedEffect(page) {
        if (page == 0) return@LaunchedEffect // never on first open
        if (soundEnabled) feedback.play()
        if (hapticEnabled) {
            if (Build.VERSION.SDK_INT >= 27) {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_RELEASE)
            } else {
                @Suppress("DEPRECATION")
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
        }
    }
}
