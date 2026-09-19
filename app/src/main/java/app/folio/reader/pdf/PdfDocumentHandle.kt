package app.folio.reader.pdf

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import app.folio.core.model.BookLocation
import app.folio.core.model.TocEntry
import app.folio.data.files.FileAccess
import app.folio.reader.api.BookOpenError
import io.legere.pdfiumandroid.api.PdfPasswordException
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import io.legere.pdfiumandroid.suspend.PdfPageKt
import io.legere.pdfiumandroid.suspend.PdfiumCoreKt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

data class PdfPageSize(val widthPoints: Int, val heightPoints: Int) {
    val aspectRatio: Float get() = if (heightPoints == 0) 1f else widthPoints.toFloat() / heightPoints
}

/** A selected or matched span of text on a page, in normalized page coordinates (0..1). */
data class PdfTextSpan(val start: Int, val end: Int, val text: String, val rects: List<RectF>)

/**
 * Wraps one open PDF. PDFium is not thread safe, so every call runs on a single-threaded
 * dispatcher and is additionally serialized by a mutex.
 */
class PdfDocumentHandle private constructor(
    private val core: PdfiumCoreKt,
    private val document: PdfDocumentKt,
    private val descriptor: ParcelFileDescriptor,
    val pageCount: Int,
    val pageSizes: List<PdfPageSize>,
) {
    private val lock = Mutex()
    private var closed = false

    suspend fun renderPage(
        index: Int,
        targetWidthPx: Int,
        crop: RectF? = null,
    ): Bitmap? = lock.withLock {
        if (closed || index !in 0 until pageCount || targetWidthPx <= 0) return@withLock null
        val size = pageSizes.getOrNull(index) ?: return@withLock null
        val fullWidth = targetWidthPx.coerceAtMost(MAX_RENDER_WIDTH)
        val fullHeight = (fullWidth / size.aspectRatio).roundToInt().coerceAtLeast(1)
        if (fullHeight > MAX_RENDER_HEIGHT) return@withLock null
        var page: PdfPageKt? = null
        try {
            page = document.openPage(index) ?: return@withLock null
            val bitmap = Bitmap.createBitmap(fullWidth, fullHeight, Bitmap.Config.ARGB_8888)
            page.renderPageBitmap(bitmap, 0, 0, fullWidth, fullHeight, renderAnnot = true)
            if (crop == null) {
                bitmap
            } else {
                val x = (crop.left * fullWidth).roundToInt().coerceIn(0, fullWidth - 1)
                val y = (crop.top * fullHeight).roundToInt().coerceIn(0, fullHeight - 1)
                val w = ((crop.width()) * fullWidth).roundToInt().coerceIn(1, fullWidth - x)
                val h = ((crop.height()) * fullHeight).roundToInt().coerceIn(1, fullHeight - y)
                val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
                if (cropped != bitmap) bitmap.recycle()
                cropped
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        } finally {
            page?.safeClose()
        }
    }

    suspend fun pageText(index: Int): String? = lock.withLock {
        if (closed || index !in 0 until pageCount) return@withLock null
        var page: PdfPageKt? = null
        try {
            page = document.openPage(index) ?: return@withLock null
            val textPage = page.openTextPage()
            val count = textPage.textPageCountChars()
            val text = if (count > 0) textPage.textPageGetText(0, count) else ""
            textPage.safeClose()
            text
        } catch (e: Exception) {
            null
        } finally {
            page?.safeClose()
        }
    }

    /** Character index nearest to a point given in normalized page coordinates. */
    suspend fun charIndexAt(index: Int, xNorm: Float, yNorm: Float): Int = lock.withLock {
        if (closed) return@withLock -1
        val size = pageSizes.getOrNull(index) ?: return@withLock -1
        var page: PdfPageKt? = null
        try {
            page = document.openPage(index) ?: return@withLock -1
            val textPage = page.openTextPage()
            val pagePoint = page.mapDeviceCoordsToPage(
                startX = 0,
                startY = 0,
                sizeX = size.widthPoints,
                sizeY = size.heightPoints,
                rotate = 0,
                deviceX = (xNorm * size.widthPoints).roundToInt(),
                deviceY = (yNorm * size.heightPoints).roundToInt(),
            )
            val tolerance = size.widthPoints * TOUCH_TOLERANCE_RATIO
            val charIndex = textPage.textPageGetCharIndexAtPos(
                pagePoint.x.toDouble(),
                pagePoint.y.toDouble(),
                tolerance.toDouble(),
                tolerance.toDouble(),
            )
            textPage.safeClose()
            charIndex
        } catch (e: Exception) {
            -1
        } finally {
            page?.safeClose()
        }
    }

    /** Text and highlight rectangles for a character range, in normalized page coordinates. */
    suspend fun span(index: Int, start: Int, end: Int): PdfTextSpan? = lock.withLock {
        if (closed || start < 0 || end < start) return@withLock null
        val size = pageSizes.getOrNull(index) ?: return@withLock null
        var page: PdfPageKt? = null
        try {
            page = document.openPage(index) ?: return@withLock null
            val textPage = page.openTextPage()
            val charCount = textPage.textPageCountChars()
            val from = start.coerceIn(0, maxOf(charCount - 1, 0))
            val length = (end - start + 1).coerceIn(0, maxOf(charCount - from, 0))
            if (length <= 0) {
                textPage.safeClose()
                return@withLock null
            }
            val text = textPage.textPageGetText(from, length).orEmpty()
            val rectCount = textPage.textPageCountRects(from, length)
            val rects = mutableListOf<RectF>()
            for (i in 0 until rectCount) {
                val pageRect = textPage.textPageGetRect(i) ?: continue
                val deviceRect: Rect = page.mapRectToDevice(
                    startX = 0,
                    startY = 0,
                    sizeX = size.widthPoints,
                    sizeY = size.heightPoints,
                    rotate = 0,
                    coords = pageRect,
                )
                rects += RectF(
                    deviceRect.left.toFloat() / size.widthPoints,
                    deviceRect.top.toFloat() / size.heightPoints,
                    deviceRect.right.toFloat() / size.widthPoints,
                    deviceRect.bottom.toFloat() / size.heightPoints,
                )
            }
            textPage.safeClose()
            PdfTextSpan(from, from + length - 1, text, rects)
        } catch (e: Exception) {
            null
        } finally {
            page?.safeClose()
        }
    }

    suspend fun tableOfContents(): List<TocEntry> = lock.withLock {
        if (closed) return@withLock emptyList()
        try {
            val out = mutableListOf<TocEntry>()
            fun walk(bookmarks: List<io.legere.pdfiumandroid.api.Bookmark>, level: Int) {
                bookmarks.forEach { bookmark ->
                    val page = bookmark.pageIdx.toInt().coerceIn(0, maxOf(pageCount - 1, 0))
                    out += TocEntry(
                        title = bookmark.title?.takeIf { it.isNotBlank() } ?: "—",
                        location = BookLocation(
                            page = page,
                            totalProgression = if (pageCount > 0) page.toDouble() / pageCount else 0.0,
                            label = "${page + 1}",
                        ),
                        level = level,
                    )
                    if (bookmark.children.isNotEmpty()) walk(bookmark.children, level + 1)
                }
            }
            walk(document.getTableOfContents(), 0)
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun metadata(): io.legere.pdfiumandroid.api.Meta? = lock.withLock {
        if (closed) return@withLock null
        try {
            document.getDocumentMeta()
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        if (closed) return
        closed = true
        runCatching { document.safeClose() }
        runCatching { descriptor.close() }
    }

    companion object {
        private const val MAX_RENDER_WIDTH = 3000
        private const val MAX_RENDER_HEIGHT = 8000
        private const val TOUCH_TOLERANCE_RATIO = 0.02f

        @OptIn(ExperimentalCoroutinesApi::class)
        val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

        suspend fun open(
            files: FileAccess,
            uri: String,
            password: String? = null,
        ): Result<PdfDocumentHandle> {
            val descriptor = files.openFileDescriptor(uri)
                ?: return Result.failure(PdfOpenException(BookOpenError.FileMissing))
            return try {
                val core = PdfiumCoreKt(dispatcher)
                val document = core.newDocument(descriptor, password)
                val count = document.getPageCount()
                if (count <= 0) {
                    document.safeClose()
                    descriptor.close()
                    return Result.failure(PdfOpenException(BookOpenError.Empty))
                }
                val sizes = (0 until count).map { index ->
                    val page = document.openPage(index)
                    val size = if (page != null) {
                        PdfPageSize(page.getPageWidthPoint(), page.getPageHeightPoint())
                    } else {
                        PdfPageSize(612, 792)
                    }
                    page?.safeClose()
                    size
                }
                Result.success(PdfDocumentHandle(core, document, descriptor, count, sizes))
            } catch (e: PdfPasswordException) {
                runCatching { descriptor.close() }
                Result.failure(
                    PdfOpenException(
                        if (password == null) BookOpenError.PasswordRequired else BookOpenError.WrongPassword,
                    ),
                )
            } catch (e: Exception) {
                runCatching { descriptor.close() }
                Result.failure(PdfOpenException(BookOpenError.Corrupted(e.message)))
            } catch (e: OutOfMemoryError) {
                runCatching { descriptor.close() }
                Result.failure(PdfOpenException(BookOpenError.Failed("out of memory")))
            }
        }
    }
}

class PdfOpenException(val error: BookOpenError) : Exception(error.toString())
