package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request

/** A [Source] that can hand back the real, directly-fetchable URL behind a [Page]. */
interface ResolvableSource : Source {

    suspend fun getImageUrl(page: Page): String

    suspend fun getImageRequest(page: Page): Request
}
