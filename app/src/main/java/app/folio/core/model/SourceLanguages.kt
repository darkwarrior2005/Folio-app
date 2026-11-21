package app.folio.core.model

import java.util.Locale

/**
 * One app-wide language choice for manga sources: "Show all" or "Only <device language>".
 * `hiddenLanguages` in [app.folio.data.settings.MangaSettings] is stored as what's hidden rather
 * than what's shown, so a newly supported language appears without a settings migration.
 */
object SourceLanguages {

    const val SHOW_ALL = "all"

    fun deviceLanguage(): String = Locale.getDefault().language

    /** Every language code seen among [available], minus whatever's hidden, "all" sorted last. */
    fun visible(available: Set<String>, hiddenLanguages: Set<String>): List<String> {
        return available.filterNot { it in hiddenLanguages }.sorted()
    }

    fun isVisible(language: String, hiddenLanguages: Set<String>): Boolean = language !in hiddenLanguages

    /** Hides every language except [language] (and itself, trivially). */
    fun onlyShow(available: Set<String>, language: String): Set<String> =
        available.filterNot { it == language }.toSet()

    fun displayName(languageCode: String): String {
        if (languageCode == SHOW_ALL) return "All languages"
        return Locale(languageCode).getDisplayName(Locale.getDefault()).let {
            it.ifBlank { languageCode }.replaceFirstChar(Char::uppercase)
        }
    }
}
