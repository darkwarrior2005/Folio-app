package app.folio.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Packs and unpacks the custom accent colour the user picks in Settings, stored as a plain [Long]
 * in [app.folio.data.settings.AppSettings].
 *
 * [Color.value] is a packed [ULong]: for an ordinary sRGB colour the 8-bit ARGB channels sit in
 * the *high* 32 bits, and the low 32 bits (colour space id and extra precision, used for wide
 * gamut colours) are all zero. Storing that with a plain `.toLong()` is safe, but reading it back
 * with `Color(it.toInt())` — which only looks at the low 32 bits — silently discards the ARGB
 * bits and yields a fully transparent black (alpha 0) for every sRGB colour. [decode] reverses
 * the packing correctly by handing the full 64 bits back to [Color], and falls back to treating
 * the value as a plain 32-bit ARGB int only when the high word is empty (so a value that was ever
 * stored the naive way still decodes to something visible instead of transparent).
 */
object AccentColor {
    fun encode(color: Color): Long = color.value.toLong()

    fun decode(raw: Long): Color {
        val bits = raw.toULong()
        return if (bits shr 32 != 0uL) Color(bits) else Color(raw.toInt())
    }
}
