package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga

/**
 * A source that can turn a URL into the manga or chapter it points at, so shared links open
 * in the app.
 *
 * @since extensions-lib 1.5
 */
interface ResolvableSource : Source {

    /**
     * What [uri] points at, or [UriType.Unknown] if this source cannot tell.
     *
     * @since extensions-lib 1.5
     */
    fun getUriType(uri: String): UriType

    /**
     * Called when [getUriType] returned [UriType.Manga].
     *
     * @since extensions-lib 1.5
     */
    suspend fun getManga(uri: String): SManga?

    /**
     * Called when [getUriType] returned [UriType.Chapter].
     *
     * @since extensions-lib 1.5
     */
    suspend fun getChapter(uri: String): SChapter?
}

sealed interface UriType {
    data object Manga : UriType
    data object Chapter : UriType
    data object Unknown : UriType
}
