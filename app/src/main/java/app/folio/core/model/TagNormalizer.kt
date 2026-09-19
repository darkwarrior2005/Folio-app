package app.folio.core.model

import java.text.Normalizer
import java.util.Locale

object TagNormalizer {

    /** Clean the display form: trim and collapse internal whitespace, keep the user's casing. */
    fun clean(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /**
     * Identity key used to detect duplicates: "Machine Learning", "machine  learning" and
     * "MACHINE LEARNING" all map to "machine learning".
     */
    fun key(raw: String): String =
        Normalizer.normalize(clean(raw), Normalizer.Form.NFKC).lowercase(Locale.ROOT)

    /**
     * Autocomplete ordering: prefix matches on the whole tag first, then prefix matches on any
     * word, then substring matches. Excludes tags whose key is in [exclude].
     */
    fun <T> suggest(
        query: String,
        candidates: List<T>,
        name: (T) -> String,
        exclude: Set<String> = emptySet(),
        limit: Int = 8,
    ): List<T> {
        val q = key(query)
        if (q.isEmpty()) return emptyList()
        return candidates
            .asSequence()
            .map { it to key(name(it)) }
            .filter { (_, k) -> k !in exclude && k.contains(q) }
            .sortedWith(
                compareBy<Pair<T, String>> { (_, k) ->
                    when {
                        k.startsWith(q) -> 0
                        k.split(' ').any { w -> w.startsWith(q) } -> 1
                        else -> 2
                    }
                }.thenBy { it.second.length }.thenBy { it.second },
            )
            .take(limit)
            .map { it.first }
            .toList()
    }
}
