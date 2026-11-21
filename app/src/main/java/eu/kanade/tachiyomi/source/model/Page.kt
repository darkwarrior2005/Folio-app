package eu.kanade.tachiyomi.source.model

import eu.kanade.tachiyomi.network.ProgressListener

open class Page(
    val index: Int,
    val url: String = "",
    var imageUrl: String? = null,
) : ProgressListener {

    val number: Int get() = index + 1

    @Volatile
    var status: Int = State.QUEUE.value

    @Volatile
    var progress: Int = 0

    override fun onProgressChange(bytesRead: Long, contentLength: Long) {
        progress = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }

    enum class State(val value: Int) {
        QUEUE(0),
        LOAD_PAGE(1),
        DOWNLOAD_IMAGE(2),
        READY(3),
        ERROR(4),
    }
}
