package eu.kanade.tachiyomi.network

fun interface ProgressListener {
    fun onProgressChange(bytesRead: Long, contentLength: Long)
}
