package app.folio.reader.pdf

import androidx.compose.runtime.rememberUpdatedState
import app.folio.reader.common.magnifyGestures
import app.folio.reader.api.ZoomNotice
import app.folio.core.model.RenderBudget
import app.folio.core.model.ZoomGate
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.model.BookLocation
import app.folio.core.model.InBookSearchHit
import app.folio.core.model.InkPoint
import app.folio.core.model.InkStroke
import app.folio.core.model.NormRect
import app.folio.core.model.TextRange
import app.folio.data.db.HighlightEntity
import app.folio.data.files.FileAccess
import app.folio.data.settings.PdfFitMode
import app.folio.data.settings.PdfSettings
import app.folio.data.settings.PdfViewMode
import app.folio.data.settings.TapZoneStyle
import app.folio.reader.api.BookOpenError
import app.folio.reader.api.ReaderController
import app.folio.reader.api.ReaderHost
import app.folio.reader.api.ReaderSelection
import app.folio.reader.api.ScribbleState
import app.folio.reader.common.InkLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MAX_SEARCH_HITS = 300

internal data class PdfSelection(
    val page: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val rects: List<RectF>,
)

@Composable
fun PdfReaderContent(host: ReaderHost, files: FileAccess) {
    val settings by host.settings.collectAsStateWithLifecycle()
    val password by host.password.collectAsStateWithLifecycle()
    val highlights by host.highlights.collectAsStateWithLifecycle()
    val scribble by host.scribble.collectAsStateWithLifecycle()
    val pageInk by host.pageInk.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var handle by remember { mutableStateOf<PdfDocumentHandle?>(null) }
    val pageCache = remember { PdfPageCache() }
    var cropBounds by remember { mutableStateOf<Map<Int, RectF?>>(emptyMap()) }

    LaunchedEffect(host.source.uri, password) {
        PdfDocumentHandle.open(files, host.source.uri, password)
            .onSuccess { document ->
                handle = document
                host.onToc(document.tableOfContents())
            }
            .onFailure { throwable ->
                val error = (throwable as? PdfOpenException)?.error ?: BookOpenError.Failed(throwable.message)
                if (error is BookOpenError.PasswordRequired || error is BookOpenError.WrongPassword) {
                    host.requestPassword()
                } else {
                    host.onError(error)
                }
            }
    }

    DisposableEffect(handle) {
        val open = handle
        onDispose {
            open?.close()
            pageCache.clear()
        }
    }

    val document = handle
    if (document == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val pdf = settings.pdf
    var selection by remember(document) { mutableStateOf<PdfSelection?>(null) }

    fun publishSelection(value: PdfSelection?, anchor: ComposeRect?) {
        selection = value
        host.onSelectionChanged(
            value?.let {
                ReaderSelection(
                    text = it.text,
                    location = document.locationOf(it.page, 0.0),
                    positionLabel = "${it.page + 1}",
                    progress = document.progressOf(it.page, 0.0),
                    page = it.page,
                    range = TextRange(it.start, it.end),
                    anchor = anchor,
                )
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }.roundToInt()

        val bookPrefs by host.bookPrefs.collectAsStateWithLifecycle()
        val locked = bookPrefs.zoomLocked == true
        val scribbling = scribble.active
        // Gesture detectors are not restarted on recomposition; read drawing mode through this.
        val currentScribbling by rememberUpdatedState(scribbling)
        var zoom by remember(document) { mutableStateOf(bookPrefs.zoom ?: 1f) }
        var pan by remember(document) { mutableStateOf(Offset.Zero) }
        // Bitmaps are rendered for the zoom the gesture settled on, not for every pinch frame.
        var settledZoom by remember(document) { mutableStateOf(zoom) }
        val budget = remember { RenderBudget.defaultBudget(Runtime.getRuntime().maxMemory()) }
        val pageAspect = document.pageSizes.firstOrNull()?.aspectRatio ?: 0.707f
        val maxRenderZoom = RenderBudget.maxZoom(viewportWidthPx.roundToInt(), pageAspect, budget, ZoomGate.MAX_ZOOM)

        fun zoomLabelFor(value: Float) = if (value > 1.01f) "${(value * 100).roundToInt()}%" else null

        fun settle() {
            if (zoom > maxRenderZoom) {
                zoom = maxRenderZoom
                host.onZoomNotice(ZoomNotice.OutOfMemory)
            }
            settledZoom = zoom
            host.onZoomLabel(zoomLabelFor(zoom))
            host.updateBookPrefs { it.copy(zoom = zoom) }
        }

        LaunchedEffect(document) { host.onZoomLabel(zoomLabelFor(zoom)) }

        // Starting to draw drops any text selection.
        LaunchedEffect(scribbling) { if (scribbling) publishSelection(null, null) }

        val initialPage = remember(document) {
            (host.initialLocation?.page ?: 0).coerceIn(0, document.pageCount - 1)
        }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)
        val pagerState = rememberPagerState(initialPage = initialPage) { document.pageCount }
        val spreads = remember(document, pdf.viewMode) { document.spreads(coverAlone = true) }
        val spreadPagerState = rememberPagerState(
            initialPage = spreads.indexOfFirst { initialPage in it }.coerceAtLeast(0),
        ) { spreads.size.coerceAtLeast(1) }

        val controller = remember(document, pdf.viewMode, spreads) {
            PdfController(
                document, pdf.viewMode, spreads, listState, pagerState, spreadPagerState, scope,
                clearSelectionAction = { publishSelection(null, null) },
                resetZoomAction = {
                    zoom = 1f
                    pan = Offset.Zero
                    settle()
                },
            )
        }

        DisposableEffect(controller) {
            host.onControllerReady(controller)
            onDispose { host.onControllerReady(null) }
        }

        LaunchedEffect(controller) {
            host.jumps.collectLatest { location -> controller.goTo(location) }
        }

        LaunchedEffect(document, pdf.viewMode) {
            when (pdf.viewMode) {
                PdfViewMode.CONTINUOUS -> snapshotFlow {
                    val index = listState.firstVisibleItemIndex
                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val fraction = if (info == null || info.size == 0) 0.0 else (-info.offset).toDouble() / info.size
                    index to fraction.coerceIn(0.0, 1.0)
                }.collectLatest { (index, fraction) ->
                    host.report(document, index, fraction)
                }
                PdfViewMode.DOUBLE_PAGE -> snapshotFlow { spreadPagerState.currentPage }.collectLatest { spread ->
                    host.report(document, spreads.getOrNull(spread)?.first ?: 0, 0.0)
                }
                else -> snapshotFlow { pagerState.currentPage }.collectLatest { page ->
                    host.report(document, page, 0.0)
                }
            }
        }

        // Detect content bounds for margin cropping, a few pages ahead of the reader.
        LaunchedEffect(pdf.cropMargins, document, pdf.viewMode) {
            if (!pdf.cropMargins) {
                cropBounds = emptyMap()
                return@LaunchedEffect
            }
            snapshotFlow {
                when (pdf.viewMode) {
                    PdfViewMode.CONTINUOUS -> listState.firstVisibleItemIndex
                    PdfViewMode.DOUBLE_PAGE -> spreads.getOrNull(spreadPagerState.currentPage)?.first ?: 0
                    else -> pagerState.currentPage
                }
            }.debounce(200).collectLatest { current ->
                (current - 1..current + 2)
                    .filter { it in 0 until document.pageCount && !cropBounds.containsKey(it) }
                    .forEach { page -> cropBounds = cropBounds + (page to document.detectContentBounds(page)) }
            }
        }

        fun onPageTap(rootX: Float) {
            if (selection != null) {
                publishSelection(null, null)
                return
            }
            val fraction = if (viewportWidthPx <= 0f) 0.5f else rootX / viewportWidthPx
            when (tapZone(fraction, settings.behavior.tapZones, settings.behavior.invertTapZones)) {
                TapAction.NEXT -> controller.nextPage()
                TapAction.PREVIOUS -> controller.previousPage()
                TapAction.TOGGLE_UI -> host.onCenterTap()
            }
        }

        fun onPageDoubleTap() {
            val next = ZoomGate.toggleDoubleTap(zoom, locked) ?: return
            zoom = next
            if (next <= 1f) pan = Offset.Zero
            settle()
        }

        val renderWidthPx = (viewportWidthPx * settledZoom.coerceAtMost(maxRenderZoom)).roundToInt().coerceAtLeast(1)

        val pageContent: @Composable (Int, Int, Boolean) -> Unit = { index, widthPx, fitPage ->
            PdfPage(
                document = document,
                index = index,
                widthPx = widthPx,
                crop = cropBounds[index],
                nightMode = pdf.nightMode,
                cache = pageCache,
                highlights = highlights,
                selection = selection?.takeIf { it.page == index },
                fitHeightPx = if (fitPage) viewportHeightPx else null,
                onSelectionChange = ::publishSelection,
                onHighlightTap = { highlight, anchor ->
                    host.onSelectionChanged(highlight.toSelection(document, anchor))
                },
                onTap = ::onPageTap,
                onDoubleTap = ::onPageDoubleTap,
                ink = pageInk[index].orEmpty(),
                scribble = scribble,
                onInkStroke = { points, heightOverWidth -> host.onInkStroke(index, points, heightOverWidth) },
                onInkErase = { point, radius, heightOverWidth -> host.onInkErase(index, point, radius, heightOverWidth) },
                onInkEraseEnd = host::onInkEraseEnd,
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(if (pdf.nightMode) Color.Black else MaterialTheme.colorScheme.surfaceVariant)
                .magnifyGestures(
                    key = document to locked,
                    zoom = { zoom },
                    onZoom = { factor, centroid ->
                        val step = ZoomGate.magnify(zoom, factor, locked) ?: return@magnifyGestures
                        val center = Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
                        val focus = centroid - center
                        pan = if (step.zoom <= 1f) Offset.Zero else (pan - focus) * (step.zoom / zoom) + focus
                        zoom = step.zoom
                    },
                    onPan = { delta ->
                        // Not gated: the lock only stops zoom changes, a zoomed page can still be panned.
                        if (zoom > 1f) {
                            val maxX = viewportWidthPx * (zoom - 1f) / 2f
                            val maxY = viewportHeightPx * (zoom - 1f) / 2f
                            pan = Offset(
                                (pan.x + delta.x).coerceIn(-maxX, maxX),
                                (pan.y + delta.y).coerceIn(-maxY, maxY),
                            )
                        } else if (currentScribbling && pdf.viewMode == PdfViewMode.CONTINUOUS) {
                            // While drawing, two fingers scroll the page list.
                            listState.dispatchRawDelta(-delta.y)
                        }
                    },
                    onEnd = { if (!locked) settle() },
                    singleFingerPan = { !currentScribbling },
                ),
        ) {
            val contentModifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = pan.x
                    translationY = pan.y
                }

            when (pdf.viewMode) {
                PdfViewMode.CONTINUOUS -> LazyColumn(
                    state = listState,
                    modifier = contentModifier,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = !scribbling,
                ) {
                    items(document.pageCount) { index -> pageContent(index, renderWidthPx, false) }
                }

                PdfViewMode.PAGED_HORIZONTAL -> HorizontalPager(pagerState, contentModifier, userScrollEnabled = !scribbling) { index ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        pageContent(index, renderWidthPx, pdf.fitMode == PdfFitMode.PAGE)
                    }
                }

                PdfViewMode.PAGED_VERTICAL -> VerticalPager(pagerState, contentModifier, userScrollEnabled = !scribbling) { index ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        pageContent(index, renderWidthPx, pdf.fitMode == PdfFitMode.PAGE)
                    }
                }

                PdfViewMode.DOUBLE_PAGE -> HorizontalPager(spreadPagerState, contentModifier, userScrollEnabled = !scribbling) { spreadIndex ->
                    val pages = spreads.getOrNull(spreadIndex) ?: IntRange.EMPTY
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val count = pages.count().coerceAtLeast(1)
                        pages.forEach { index ->
                            Box(Modifier.weight(1f)) { pageContent(index, renderWidthPx / count, true) }
                        }
                    }
                }
            }
        }
    }
}

private fun ReaderHost.report(document: PdfDocumentHandle, page: Int, fraction: Double) {
    onProgress(
        document.locationOf(page, fraction),
        document.progressOf(page, fraction),
        page,
        document.pageCount,
        "${page + 1}",
    )
}

private class PdfController(
    private val document: PdfDocumentHandle,
    private val viewMode: PdfViewMode,
    private val spreads: List<IntRange>,
    private val listState: androidx.compose.foundation.lazy.LazyListState,
    private val pagerState: androidx.compose.foundation.pager.PagerState,
    private val spreadPagerState: androidx.compose.foundation.pager.PagerState,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val clearSelectionAction: () -> Unit,
    private val resetZoomAction: () -> Unit,
) : ReaderController {
    override val supportsTextSelection = true
    override val supportsSearch = true
    override val supportsPageThumbnails = true

    override fun goTo(location: BookLocation) {
        val page = (location.page ?: 0).coerceIn(0, (document.pageCount - 1).coerceAtLeast(0))
        scope.launch {
            when (viewMode) {
                PdfViewMode.CONTINUOUS -> listState.scrollToItem(page)
                PdfViewMode.DOUBLE_PAGE ->
                    spreadPagerState.scrollToPage(spreads.indexOfFirst { page in it }.coerceAtLeast(0))
                else -> pagerState.scrollToPage(page)
            }
        }
    }

    override fun nextPage() = step(1)

    override fun previousPage() = step(-1)

    private fun step(delta: Int) {
        scope.launch {
            when (viewMode) {
                PdfViewMode.CONTINUOUS -> listState.animateScrollToItem(
                    (listState.firstVisibleItemIndex + delta).coerceIn(0, (document.pageCount - 1).coerceAtLeast(0)),
                )
                PdfViewMode.DOUBLE_PAGE -> spreadPagerState.animateScrollToPage(
                    (spreadPagerState.currentPage + delta).coerceIn(0, spreads.lastIndex.coerceAtLeast(0)),
                )
                else -> pagerState.animateScrollToPage(
                    (pagerState.currentPage + delta).coerceIn(0, (document.pageCount - 1).coerceAtLeast(0)),
                )
            }
        }
    }

    override fun clearSelection() = clearSelectionAction()

    override fun resetZoom() = resetZoomAction()

    override suspend fun search(query: String): List<InBookSearchHit> = document.search(query)

    override suspend fun thumbnail(page: Int, widthPx: Int): Bitmap? = document.renderPage(page, widthPx)
}

@Composable
private fun PdfPage(
    document: PdfDocumentHandle,
    index: Int,
    widthPx: Int,
    crop: RectF?,
    nightMode: Boolean,
    cache: PdfPageCache,
    highlights: List<HighlightEntity>,
    selection: PdfSelection?,
    fitHeightPx: Int?,
    onSelectionChange: (PdfSelection?, ComposeRect?) -> Unit,
    onHighlightTap: (HighlightEntity, ComposeRect?) -> Unit,
    onTap: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    ink: List<InkStroke>,
    scribble: ScribbleState,
    onInkStroke: (List<InkPoint>, Float) -> Unit,
    onInkErase: (InkPoint, Float, Float) -> Unit,
    onInkEraseEnd: () -> Unit,
) {
    val pageSize = document.pageSizes.getOrNull(index) ?: return
    val ratio = crop?.takeIf { it.width() > 0f && it.height() > 0f }
        ?.let { pageSize.aspectRatio * (it.width() / it.height()) }
        ?: pageSize.aspectRatio

    val bitmap by produceState<Bitmap?>(null, index, widthPx, crop, document) {
        val cached = cache.get(index, widthPx, crop != null)
        value = cached ?: document.renderPage(index, widthPx, crop)?.also {
            cache.put(index, widthPx, crop != null, it)
        }
    }

    val pageHighlights = remember(highlights, index) {
        highlights.filter { it.page == index && it.rangeStart != null }
    }
    val highlightRects by produceState(
        emptyList<Pair<HighlightEntity, List<RectF>>>(),
        pageHighlights,
        document,
    ) {
        value = pageHighlights.mapNotNull { highlight ->
            document.span(index, highlight.rangeStart ?: 0, highlight.rangeEnd ?: 0)
                ?.let { highlight to it.rects }
        }
    }

    val scope = rememberCoroutineScope()
    var anchorChar by remember(index) { mutableStateOf(-1) }
    // Kept as coordinates, not a root offset: pages sit inside a zoom graphicsLayer, so page-local
    // points must be mapped through the scale to find where they really are on screen.
    var pageCoordinates by remember(index) { mutableStateOf<LayoutCoordinates?>(null) }
    // The gesture detectors below are not restarted on recomposition; read the latest callbacks
    // so state such as the zoom lock is never stale.
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnHighlightTap by rememberUpdatedState(onHighlightTap)
    var latestSelection by remember(index) { mutableStateOf<PdfSelection?>(null) }

    val heightModifier = if (fitHeightPx != null) {
        // The page box must match the page's shape: touches and highlight rects are normalized
        // against this box, so letterboxing the image inside a full-screen box shifts them.
        Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
            .aspectRatio(ratio.coerceAtLeast(0.1f))
    } else {
        Modifier.fillMaxWidth().aspectRatio(ratio.coerceAtLeast(0.1f))
    }

    Box(
        heightModifier
            .background(if (nightMode) Color(0xFF101010) else Color.White)
            .onGloballyPositioned { pageCoordinates = it }
            // While drawing, a finger is a pen: no selection, highlight taps or page-turn taps.
            .then(if (scribble.active) Modifier else Modifier.pointerInput(document, index) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        scope.launch {
                            val charIndex = document.charIndexAt(index, offset.x / width, offset.y / height)
                            if (charIndex >= 0) {
                                anchorChar = charIndex
                                document.span(index, charIndex, charIndex)?.let { span ->
                                    val value = PdfSelection(index, span.start, span.end, span.text, span.rects)
                                    latestSelection = value
                                    onSelectionChange(value, null)
                                }
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val position = change.position
                        scope.launch {
                            val charIndex = document.charIndexAt(index, position.x / width, position.y / height)
                            if (charIndex >= 0 && anchorChar >= 0) {
                                document.span(
                                    index,
                                    minOf(anchorChar, charIndex),
                                    maxOf(anchorChar, charIndex),
                                )?.let { span ->
                                    val value = PdfSelection(index, span.start, span.end, span.text, span.rects)
                                    latestSelection = value
                                    onSelectionChange(value, null)
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        val current = latestSelection ?: return@detectDragGesturesAfterLongPress
                        val rect = current.rects.firstOrNull()
                        onSelectionChange(current, rect?.toRootRect(pageCoordinates, size.width, size.height))
                    },
                )
            }
            .pointerInput(highlightRects, index) {
                detectTapGestures(
                    onDoubleTap = { currentOnDoubleTap() },
                    onTap = { offset ->
                        val xNorm = offset.x / size.width
                        val yNorm = offset.y / size.height
                        // Lines of small print are only a few pixels tall; allow a finger's slack.
                        val slackX = HIGHLIGHT_TOUCH_SLACK.toPx() / size.width
                        val slackY = HIGHLIGHT_TOUCH_SLACK.toPx() / size.height
                        val hit = highlightRects.firstOrNull { (_, rects) ->
                            rects.any { rect ->
                                xNorm >= rect.left - slackX && xNorm <= rect.right + slackX &&
                                    yNorm >= rect.top - slackY && yNorm <= rect.bottom + slackY
                            }
                        }
                        if (hit != null) {
                            currentOnHighlightTap(
                                hit.first,
                                hit.second.first().toRootRect(pageCoordinates, size.width, size.height),
                            )
                        } else {
                            currentOnTap(pageCoordinates?.localToRoot(offset)?.x ?: offset.x)
                        }
                    },
                )
            }),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null && !image.isRecycled) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = if (nightMode) ColorFilter.colorMatrix(INVERT_MATRIX) else null,
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            highlightRects.forEach { (highlight, rects) ->
                val color = Color(highlight.color.argb).copy(alpha = 0.32f)
                rects.forEach { rect -> drawNormalizedRect(rect, color) }
            }
            selection?.rects?.forEach { rect -> drawNormalizedRect(rect, SELECTION_COLOR) }
        }

        InkLayer(
            strokes = ink,
            scribble = scribble,
            crop = crop?.takeIf { it.width() > 0f && it.height() > 0f }?.let { NormRect(it.left, it.top, it.right, it.bottom) },
            multiplyHighlighter = !nightMode,
            onStroke = onInkStroke,
            onErase = onInkErase,
            onEraseEnd = onInkEraseEnd,
            // Night mode color-inverts the page bitmap (see INVERT_MATRIX above) but not the ink
            // above it; invert PEN strokes to match so the default near-black pen stays visible.
            invertPen = nightMode,
        )
    }
}

private fun RectF.toRootRect(coordinates: LayoutCoordinates?, widthPx: Int, heightPx: Int): ComposeRect? {
    val attached = coordinates?.takeIf { it.isAttached } ?: return null
    val topLeft = attached.localToRoot(Offset(left * widthPx, top * heightPx))
    val bottomRight = attached.localToRoot(Offset(right * widthPx, bottom * heightPx))
    return ComposeRect(topLeft, bottomRight)
}

private fun DrawScope.drawNormalizedRect(rect: RectF, color: Color) {
    val width = (rect.right - rect.left) * size.width
    val height = (rect.bottom - rect.top) * size.height
    if (width <= 0f || height <= 0f) return
    drawRect(
        color = color,
        topLeft = Offset(rect.left * size.width, rect.top * size.height),
        size = GeometrySize(width, height),
    )
}

private fun HighlightEntity.toSelection(document: PdfDocumentHandle, anchor: ComposeRect?): ReaderSelection =
    ReaderSelection(
        text = text,
        location = document.locationOf(page ?: 0, 0.0),
        positionLabel = positionLabel,
        progress = progress,
        page = page,
        range = if (rangeStart != null && rangeEnd != null) TextRange(rangeStart, rangeEnd) else null,
        anchor = anchor,
        existingHighlightId = id,
    )

private val HIGHLIGHT_TOUCH_SLACK = 12.dp

internal enum class TapAction { NEXT, PREVIOUS, TOGGLE_UI }

internal fun tapZone(xFraction: Float, style: TapZoneStyle, inverted: Boolean): TapAction {
    val action = when (style) {
        TapZoneStyle.DISABLED -> TapAction.TOGGLE_UI
        TapZoneStyle.SIDES -> when {
            xFraction < 0.3f -> TapAction.PREVIOUS
            xFraction > 0.7f -> TapAction.NEXT
            else -> TapAction.TOGGLE_UI
        }
        TapZoneStyle.EDGES -> when {
            xFraction < 0.15f -> TapAction.PREVIOUS
            xFraction > 0.85f -> TapAction.NEXT
            else -> TapAction.TOGGLE_UI
        }
        TapZoneStyle.RIGHT_ONLY -> if (xFraction > 0.75f) TapAction.NEXT else TapAction.TOGGLE_UI
    }
    return if (!inverted) {
        action
    } else {
        when (action) {
            TapAction.NEXT -> TapAction.PREVIOUS
            TapAction.PREVIOUS -> TapAction.NEXT
            TapAction.TOGGLE_UI -> TapAction.TOGGLE_UI
        }
    }
}

private val SELECTION_COLOR = Color(0xFF4A90D9).copy(alpha = 0.35f)

private val INVERT_MATRIX = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

// ---- Document helpers ----------------------------------------------------------

internal fun PdfDocumentHandle.locationOf(page: Int, fraction: Double): BookLocation = BookLocation(
    page = page,
    offset = fraction,
    totalProgression = progressOf(page, fraction).toDouble(),
    label = "${page + 1}",
)

internal fun PdfDocumentHandle.progressOf(page: Int, fraction: Double): Float =
    if (pageCount <= 0) 0f else ((page + fraction) / pageCount).toFloat().coerceIn(0f, 1f)

/** Page groups for double-page mode; the cover is shown alone, like a printed book. */
internal fun PdfDocumentHandle.spreads(coverAlone: Boolean): List<IntRange> {
    if (pageCount <= 0) return emptyList()
    val out = mutableListOf<IntRange>()
    var index = 0
    if (coverAlone) {
        out += 0..0
        index = 1
    }
    while (index < pageCount) {
        out += index..(index + 1).coerceAtMost(pageCount - 1)
        index += 2
    }
    return out
}

internal suspend fun PdfDocumentHandle.search(query: String): List<InBookSearchHit> =
    withContext(Dispatchers.Default) {
        val needle = query.trim()
        if (needle.length < 2) return@withContext emptyList()
        val hits = mutableListOf<InBookSearchHit>()
        for (page in 0 until pageCount) {
            val text = pageText(page) ?: continue
            var index = text.indexOf(needle, ignoreCase = true)
            while (index >= 0) {
                val from = (index - 45).coerceAtLeast(0)
                val to = (index + needle.length + 60).coerceAtMost(text.length)
                hits += InBookSearchHit(
                    location = locationOf(page, 0.0),
                    snippet = text.substring(from, to).replace('\n', ' ').trim(),
                    label = "${page + 1}",
                    range = TextRange(index, index + needle.length - 1),
                )
                if (hits.size >= MAX_SEARCH_HITS) return@withContext hits
                index = text.indexOf(needle, index + needle.length, ignoreCase = true)
            }
        }
        hits
    }

/**
 * Finds the printed area of a page so margins can be cropped away. Returns null when the page
 * already fills its area, so cropping never makes a normal page worse.
 */
internal suspend fun PdfDocumentHandle.detectContentBounds(page: Int): RectF? =
    withContext(Dispatchers.Default) {
        val probe = renderPage(page, CROP_PROBE_WIDTH) ?: return@withContext null
        try {
            val width = probe.width
            val height = probe.height
            val pixels = IntArray(width * height)
            probe.getPixels(pixels, 0, width, 0, 0, width, height)
            var left = width
            var right = -1
            var top = height
            var bottom = -1
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]
                    val luminance =
                        ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
                    if (luminance < CROP_INK_THRESHOLD) {
                        if (x < left) left = x
                        if (x > right) right = x
                        if (y < top) top = y
                        if (y > bottom) bottom = y
                    }
                }
            }
            if (right < 0 || bottom < 0) return@withContext null
            val padX = width * CROP_PADDING
            val padY = height * CROP_PADDING
            val bounds = RectF(
                ((left - padX) / width).coerceIn(0f, 1f),
                ((top - padY) / height).coerceIn(0f, 1f),
                ((right + padX) / width).coerceIn(0f, 1f),
                ((bottom + padY) / height).coerceIn(0f, 1f),
            )
            val savings = 1f - bounds.width() * bounds.height()
            if (savings < MIN_CROP_SAVINGS || bounds.width() <= 0.2f || bounds.height() <= 0.2f) null else bounds
        } catch (e: Exception) {
            null
        } finally {
            if (!probe.isRecycled) probe.recycle()
        }
    }

private const val CROP_PROBE_WIDTH = 180
private const val CROP_INK_THRESHOLD = 235
private const val CROP_PADDING = 0.012f
private const val MIN_CROP_SAVINGS = 0.06f
