package app.folio.core.model

/**
 * Which source languages the user wants to see. Stored as the languages they hid, so a language
 * a newly added repository brings shows up until they say otherwise.
 *
 * Language codes are the extensions' own: "en", "pt-BR", "zh-Hans", "all" for a source that
 * covers many languages, and "" or "other" when it does not say.
 */
object SourceLanguages {

    /** The code a language is filed under; an unknown language is "other". */
    fun normalize(lang: String): String = lang.trim().ifEmpty { OTHER }

    fun isShown(lang: String, hidden: Set<String>): Boolean = normalize(lang) !in hidden

    /**
     * An extension is shown when its own language is, or, for one that bundles several languages
     * ("all"), when any of its sources is in a language the user kept.
     */
    fun isExtensionShown(lang: String, sourceLangs: Collection<String>, hidden: Set<String>): Boolean {
        if (hidden.isEmpty()) return true
        if (normalize(lang) == ALL && sourceLangs.isNotEmpty()) return sourceLangs.any { isShown(it, hidden) }
        return isShown(lang, hidden)
    }

    /** Every language in [langs], plus any hidden one so it can be turned back on. */
    fun choices(langs: Collection<String>, hidden: Set<String>): List<String> =
        (langs.map(::normalize) + hidden).distinct()

    /** The languages to hide so only [keep] (and "all", which spans languages) stay. */
    fun hideAllBut(choices: Collection<String>, keep: Collection<String>): Set<String> {
        val kept = keep.map(::normalize).toSet() + ALL
        return choices.map(::normalize).filterNot { choice ->
            choice in kept || kept.any { base -> choice.substringBefore('-').equals(base, ignoreCase = true) }
        }.toSet()
    }

    const val ALL = "all"
    const val OTHER = "other"
}
