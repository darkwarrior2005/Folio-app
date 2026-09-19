package app.folio.core.model

import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

/**
 * One set of book metadata. A book carries two of these: what extraction found in the file
 * ("imported") and what the user typed ("overrides"). A null override means "use imported".
 */
@Serializable
data class BookMetadata(
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "author") val author: String? = null,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "series") val series: String? = null,
    @ColumnInfo(name = "volume") val volume: Double? = null,
    @ColumnInfo(name = "year") val year: Int? = null,
    @ColumnInfo(name = "language") val language: String? = null,
    @ColumnInfo(name = "publisher") val publisher: String? = null,
)

object MetadataResolver {

    /** Merge imported metadata with user overrides. Overrides always win when present. */
    fun resolve(imported: BookMetadata, overrides: BookMetadata): BookMetadata = BookMetadata(
        title = overrides.title ?: imported.title,
        author = overrides.author ?: imported.author,
        description = overrides.description ?: imported.description,
        series = overrides.series ?: imported.series,
        volume = overrides.volume ?: imported.volume,
        year = overrides.year ?: imported.year,
        language = overrides.language ?: imported.language,
        publisher = overrides.publisher ?: imported.publisher,
    )

    fun displayTitle(imported: BookMetadata, overrides: BookMetadata, fileName: String): String =
        overrides.title?.takeIf { it.isNotBlank() }
            ?: imported.title?.takeIf { it.isNotBlank() }
            ?: titleFromFileName(fileName)

    /**
     * Compute the override to store when the user edits a field: if the edited value equals
     * the imported value, no override is kept, so later rescans can still improve it.
     */
    fun <T> overrideFor(edited: T?, imported: T?): T? {
        if (edited is String && edited.isBlank()) return null
        return if (edited == imported) null else edited
    }

    /** "The.Three.Body.Problem_English (2008).pdf" -> "The Three Body Problem English (2008)". */
    fun titleFromFileName(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
        val spaced = base
            .replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return spaced.ifBlank { fileName }
    }

    /** Sort key: case-insensitive, accent-insensitive, leading English articles ignored. */
    fun sortKey(title: String): String {
        val lowered = title.trim().lowercase(Locale.ROOT)
        val noArticle = lowered.replaceFirst(Regex("^(the|a|an)\\s+"), "")
        return Normalizer.normalize(noArticle, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
    }
}
