package app.folio.reader

/**
 * Fonts bundled with the app. Everything ships in assets, so reading works with no network and
 * the same families are offered in the EPUB reader, the text reader and the settings screen.
 */
data class ReaderFont(
    val displayName: String,
    val assetPath: String?,
    val cssStack: String,
    val italicAssetPath: String? = null,
) {
    val isBundled: Boolean get() = assetPath != null
}

object ReaderFonts {

    val Literata = ReaderFont("Literata", "fonts/literata.ttf", "\"Literata\", Georgia, serif", "fonts/literata_italic.ttf")
    val Lora = ReaderFont("Lora", "fonts/lora.ttf", "\"Lora\", Georgia, serif", "fonts/lora_italic.ttf")
    val SourceSerif = ReaderFont("Source Serif", "fonts/sourceserif4.ttf", "\"Source Serif\", Georgia, serif")
    val Inter = ReaderFont("Inter", "fonts/inter.ttf", "\"Inter\", system-ui, sans-serif")
    val Atkinson = ReaderFont("Atkinson Hyperlegible", "fonts/atkinson_regular.ttf", "\"Atkinson Hyperlegible\", sans-serif")
    val JetBrainsMono = ReaderFont("JetBrains Mono", "fonts/jetbrainsmono.ttf", "\"JetBrains Mono\", monospace")
    val Fraunces = ReaderFont("Fraunces", "fonts/fraunces.ttf", "\"Fraunces\", Georgia, serif")
    val SystemSerif = ReaderFont("System serif", null, "serif")
    val SystemSans = ReaderFont("System sans", null, "sans-serif")
    val OpenDyslexic = ReaderFont("OpenDyslexic", null, "OpenDyslexic, sans-serif")

    /** Offered in the reader settings, in the order shown. */
    val all: List<ReaderFont> = listOf(
        Literata, Lora, SourceSerif, Fraunces, Inter, Atkinson, JetBrainsMono, SystemSerif, SystemSans, OpenDyslexic,
    )

    /** Families with a bundled file, which the EPUB navigator must be told about. */
    val bundled: List<ReaderFont> = all.filter { it.isBundled }

    fun byName(name: String): ReaderFont = all.firstOrNull { it.displayName == name } ?: Literata
}
