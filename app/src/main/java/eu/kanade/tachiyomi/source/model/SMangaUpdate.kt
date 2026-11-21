package eu.kanade.tachiyomi.source.model

/**
 * Null fields mean the source said nothing about that field, so an existing value should be kept.
 */
data class SMangaUpdate(
    val url: String,
    val title: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Int? = null,
    val thumbnailUrl: String? = null,
    val updateStrategy: UpdateStrategy? = null,
    val initialized: Boolean? = null,
)
