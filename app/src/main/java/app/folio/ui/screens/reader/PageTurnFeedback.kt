package app.folio.ui.screens.reader

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * A soft paper whisper and a light tick when the page turns. The sound is made on the phone
 * (filtered noise, a fraction of a second, quiet) and plays at the system-sounds volume, so silent
 * mode quiets it; the tick follows the system's haptics setting.
 */
class PageTurnFeedback {

    private var track: AudioTrack? = null

    fun play(view: View, sound: Boolean, haptic: Boolean) {
        if (haptic) {
            val effect = if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_RELEASE else HapticFeedbackConstants.CLOCK_TICK
            view.performHapticFeedback(effect)
        }
        if (sound) {
            if (track == null) track = runCatching { buildTrack() }.getOrNull()
            track?.let { t ->
                runCatching {
                    t.stop()
                    t.reloadStaticData()
                    t.play()
                }
            }
        }
    }

    fun release() {
        runCatching { track?.release() }
        track = null
    }

    private fun buildTrack(): AudioTrack {
        val rate = 22_050
        val samples = (rate * 0.11).toInt()
        val pcm = ShortArray(samples)
        val random = Random(7)
        var low = 0.0
        var previous = 0.0
        for (i in 0 until samples) {
            val t = i.toDouble() / samples
            // Rises quickly, fades slowly: the brush of a page edge.
            val envelope = (1 - exp(-t * 40)) * exp(-t * 6.5) * (0.85 + 0.15 * sin(t * PI * 9))
            val noise = random.nextDouble(-1.0, 1.0)
            low += (noise - low) * 0.35 // soften the hiss
            val band = low - previous * 0.6 // and take away the rumble
            previous = low
            pcm[i] = (band * envelope * 0.22 * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }
}
