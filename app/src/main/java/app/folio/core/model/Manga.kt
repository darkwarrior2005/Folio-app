package app.folio.core.model

/** A manga as known to the library, independent of how it's stored or where it came from. */
data class Manga(
    val id: Long,
    val sourceId: Long,
    val url: String,
    val title: String,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: Int = 0,
    val thumbnailUrl: String? = null,
    val favorite: Boolean = false,
    val initialized: Boolean = false,
    val dateAdded: Long = 0,
    val lastUpdate: Long = 0,
)

/** A manga chapter, identified by its [url] rather than its position in the list. */
data class MangaChapter(
    val id: Long = 0,
    val mangaId: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
    val lastPageRead: Int = 0,
    val pagesCount: Int = 0,
    val chapterNumber: Float = -1f,
    val dateUpload: Long = 0,
    val dateFetch: Long = 0,
    val sourceOrder: Int = 0,
)

/**
 * Thrown instead of wiping a manga's chapters when a refresh comes back empty.
 *
 * @param hadChapters whether the manga already had chapters before this refresh - a hiccup
 * (network blip, a Cloudflare check) if so, or a source genuinely listing none (MangaDex hiding a
 * licensed series) if not. The UI tells these apart (see RefreshError / "No chapters found").
 */
class NoChaptersException(val hadChapters: Boolean) : Exception(
    if (hadChapters) "The source returned no chapters this time" else "No chapters found",
)
