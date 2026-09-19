package app.folio.reader.pdf

import android.graphics.Bitmap
import android.util.LruCache

/** Keeps recently rendered pages around so scrolling back is instant, bounded by memory. */
class PdfPageCache(maxBytes: Int = defaultMaxBytes()) {

    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    private fun key(page: Int, width: Int, crop: Boolean) = "$page@$width${if (crop) "c" else ""}"

    fun get(page: Int, width: Int, crop: Boolean): Bitmap? =
        cache.get(key(page, width, crop))?.takeIf { !it.isRecycled }

    fun put(page: Int, width: Int, crop: Boolean, bitmap: Bitmap) {
        cache.put(key(page, width, crop), bitmap)
    }

    fun clear() = cache.evictAll()

    companion object {
        fun defaultMaxBytes(): Int {
            val max = Runtime.getRuntime().maxMemory()
            return (max / 5).coerceAtMost(96L * 1024 * 1024).toInt()
        }
    }
}
