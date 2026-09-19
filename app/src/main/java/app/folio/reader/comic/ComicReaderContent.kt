package app.folio.reader.comic

import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import app.folio.reader.common.magnifyGestures
import app.folio.reader.api.ZoomNotice
import app.folio.core.model.RenderBudget
import app.folio.core.model.ZoomGate
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.model.BookLocation
import app.folio.core.model.InkPoint
import app.folio.core.model.InkStroke
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.settings.ComicDirection
import app.folio.data.settings.ComicFitMode
import app.folio.reader.api.BookOpenError
import app.folio.reader.api.ReaderController
import app.folio.reader.api.ReaderHost
import app.folio.reader.api.ScribbleState
import app.folio.reader.common.InkLayer
import app.folio.reader.pdf.TapAction
import app.folio.reader.pdf.tapZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Typical manga/comic page shape, used only to size the memory budget. */
private const val COMIC_PAGE_ASPECT = 0.66f

@Composable
fun ComicReaderContent(host: ReaderHost, files: FileAccess, cache: CacheStore) {
    val settings by host.settings.collectAsStateWithLifecycle()
    val bookPrefs by host.bookPrefs.collectAsStateWithLifecycle()
    val scribble by host.scribble.collectAsStateWithLifecycle()
    val pageInk by host.pageInk.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf<ComicSource?>(null) }
    val pageCache = remember { ComicPageCache() }

    LaunchedEffect(host.source.uri) {
        ComicSources.open(files, cache, host.source)
            .onSuccess { comic ->
                source = comic
                host.onToc(comic.chapters())
                // A manga that declares right-to-left opens that way the first time.
                if (host.bookPrefs.value.comicDirection == null) {
                    val rtl = comic.comicInfo()?.rightToLeft
                    if (rtl == true) {
                        host.updateBookPrefs { it.copy(comicDirection = ComicDirection.RTL) }
                    }
                }
            }
            .onFailure { throwable ->
                host.onError((throwable as? ComicOpenException)?.error ?: BookOpenError.Failed(throwable.message))
            }
    }

    DisposableEffect(source) {
        val open = source
        onDispose {
            open?.close()
            pageCache.clear()
        }
    }

    val comic = source
    if (comic == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val direction = bookPrefs.comicDirection ?: settings.comic.direction
    val fitMode = bookPrefs.comicFitMode ?: settings.comic.fitMode
    val doublePage = bookPrefs.comicDoublePage ?: settings.comic.doublePage

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }.roundToInt()

        val locked = bookPrefs.zoomLocked == true
        val scribbling = scribble.active
        val currentScribbling by rememberUpdatedState(scribbling)
        var zoom by remember(comic) { mutableStateOf(bookPrefs.zoom ?: 1f) }
        var pan by remember(comic) { mutableStateOf(Offset.Zero) }
        // Pages are decoded for the zoom the gesture settled on, not for every pinch frame.
        var settledZoom by remember(comic) { mutableStateOf(zoom) }
        val budget = remember { RenderBudget.defaultBudget(Runtime.getRuntime().maxMemory()) }
        val maxRenderZoom = RenderBudget.maxZoom(viewportWidthPx.roundToInt(), COMIC_PAGE_ASPECT, budget, ZoomGate.MAX_ZOOM)

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

        LaunchedEffect(comic) { host.onZoomLabel(zoomLabelFor(zoom)) }

        val initialPage = remember(comic) {
            (host.initialLocation?.page ?: 0).coerceIn(0, (comic.pageCount - 1).coerceAtLeast(0))
        }
        val spreads = remember(comic, doublePage, settings.comic.coverAlone) {
            if (doublePage) comic.spreads(settings.comic.coverAlone) else (0 until comic.pageCount).map { it..it }
        }
        val pagerState = rememberPagerState(
            initialPage = spreads.indexOfFirst { initialPage in it }.coerceAtLeast(0),
        ) { spreads.size.coerceAtLeast(1) }
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPage)

        val controller = remember(comic, direction, spreads) {
            ComicController(comic, spreads, direction, pagerState, listState, scope) {
                zoom = 1f
                pan = Offset.Zero
                settle()
            }
        }

        DisposableEffect(controller) {
            host.onControllerReady(controller)
            onDispose { host.onControllerReady(null) }
        }

        LaunchedEffect(controller) {
            host.jumps.collectLatest { location -> controller.goTo(location) }
        }

        LaunchedEffect(comic, direction, spreads) {
            if (direction == ComicDirection.VERTICAL) {
                snapshotFlow {
                    val index = listState.firstVisibleItemIndex
                    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                    val fraction = if (info == null || info.size == 0) 0.0 else (-info.offset).toDouble() / info.size
                    index to fraction.coerceIn(0.0, 1.0)
                }.collectLatest { (index, fraction) -> host.reportComic(comic.pageCount, index, fraction) }
            } else {
                snapshotFlow { pagerState.currentPage }.collectLatest { spreadIndex ->
                    val page = spreads.getOrNull(spreadIndex)?.first ?: 0
                    host.reportComic(comic.pageCount, page, 0.0)
                }
            }
        }

        // Decode the next pages ahead of the reader so page turns feel instant.
        LaunchedEffect(comic, pagerState.currentPage, listState.firstVisibleItemIndex, viewportWidthPx) {
            val current = if (direction == ComicDirection.VERTICAL) {
                listState.firstVisibleItemIndex
            } else {
                spreads.getOrNull(pagerState.currentPage)?.first ?: 0
            }
            val width = viewportWidthPx.roundToInt().coerceAtLeast(1)
            withContext(Dispatchers.IO) {
                listOf(current + 1, current + 2, current - 1).forEach { page ->
                    if (page in 0 until comic.pageCount && pageCache.get(page, width) == null) {
                        comic.pageBytes(page)?.let { bytes ->
                            ComicSources.decode(bytes, width)?.let { pageCache.put(page, width, it) }
                        }
                    }
                }
            }
        }

        fun onTap(rootX: Float) {
            val fraction = if (viewportWidthPx <= 0f) 0.5f else rootX / viewportWidthPx
            val action = tapZone(fraction, settings.behavior.tapZones, settings.behavior.invertTapZones)
            val flipped = if (direction == ComicDirection.RTL) {
                when (action) {
                    TapAction.NEXT -> TapAction.PREVIOUS
                    TapAction.PREVIOUS -> TapAction.NEXT
                    TapAction.TOGGLE_UI -> TapAction.TOGGLE_UI
                }
            } else {
                action
            }
            when (flipped) {
                TapAction.NEXT -> controller.nextPage()
                TapAction.PREVIOUS -> controller.previousPage()
                TapAction.TOGGLE_UI -> host.onCenterTap()
            }
        }

        fun onDoubleTap() {
            val next = ZoomGate.toggleDoubleTap(zoom, locked) ?: return
            zoom = next
            if (next <= 1f) pan = Offset.Zero
            settle()
        }

        val renderWidthPx = (viewportWidthPx * settledZoom.coerceAtMost(maxRenderZoom)).roundToInt().coerceAtLeast(1)

        Box(
            Modifier
                .fillMaxSize()
                .magnifyGestures(
                    key = comic to locked,
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
                        } else if (currentScribbling && direction == ComicDirection.VERTICAL) {
                            // While drawing, two fingers scroll a webtoon.
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

            if (direction == ComicDirection.VERTICAL) {
                LazyColumn(
                    state = listState,
                    modifier = contentModifier,
                    verticalArrangement = if (settings.comic.gapBetweenPages) {
                        Arrangement.spacedBy(4.dp)
                    } else {
                        Arrangement.Top
                    },
                    userScrollEnabled = !scribbling,
                ) {
                    items(comic.pageCount) { index ->
                        ComicPage(
                            comic = comic,
                            index = index,
                            widthPx = renderWidthPx,
                            cache = pageCache,
                            fitMode = ComicFitMode.WIDTH,
                            viewportHeightPx = viewportHeightPx,
                            onTap = ::onTap,
                            onDoubleTap = ::onDoubleTap,
                            ink = pageInk[index].orEmpty(),
                            scribble = scribble,
                            onInkStroke = { points, heightOverWidth -> host.onInkStroke(index, points, heightOverWidth) },
                            onInkErase = { point, radius, heightOverWidth -> host.onInkErase(index, point, radius, heightOverWidth) },
                            onInkEraseEnd = host::onInkEraseEnd,
                        )
                    }
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = contentModifier,
                    reverseLayout = direction == ComicDirection.RTL,
                    userScrollEnabled = !scribbling,
                ) { spreadIndex ->
                    val pages = spreads.getOrNull(spreadIndex) ?: IntRange.EMPTY
                    val ordered = pages.toList().let { if (direction == ComicDirection.RTL) it.reversed() else it }
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val count = ordered.size.coerceAtLeast(1)
                        ordered.forEach { index ->
                            // Restores the old centre-crop: a page bigger than its cell must not
                            // overflow into the neighbouring page during a swipe.
                            Box(Modifier.weight(1f).clipToBounds(), contentAlignment = Alignment.Center) {
                                ComicPage(
                                    comic = comic,
                                    index = index,
                                    widthPx = renderWidthPx / count,
                                    cache = pageCache,
                                    fitMode = fitMode,
                                    viewportHeightPx = viewportHeightPx,
                                    onTap = ::onTap,
                                    onDoubleTap = ::onDoubleTap,
                                    ink = pageInk[index].orEmpty(),
                                    scribble = scribble,
                                    onInkStroke = { points, heightOverWidth -> host.onInkStroke(index, points, heightOverWidth) },
                                    onInkErase = { point, radius, heightOverWidth -> host.onInkErase(index, point, radius, heightOverWidth) },
                                    onInkEraseEnd = host::onInkEraseEnd,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComicPage(
    comic: ComicSource,
    index: Int,
    widthPx: Int,
    cache: ComicPageCache,
    fitMode: ComicFitMode,
    viewportHeightPx: Int,
    onTap: (Float) -> Unit,
    onDoubleTap: () -> Unit,
    ink: List<InkStroke>,
    scribble: ScribbleState,
    onInkStroke: (List<InkPoint>, Float) -> Unit,
    onInkErase: (InkPoint, Float, Float) -> Unit,
    onInkEraseEnd: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(null, index, widthPx, comic) {
        val cached = cache.get(index, widthPx)
        value = cached ?: withContext(Dispatchers.IO) {
            comic.pageBytes(index)?.let { bytes -> ComicSources.decode(bytes, widthPx) }
        }?.also { cache.put(index, widthPx, it) }
    }

    val image = bitmap
    // Tap zones are screen zones: map the page-local tap through the zoom and double-page layout.
    var pageCoordinates by remember(index) { mutableStateOf<LayoutCoordinates?>(null) }
    // The tap detector is not restarted on recomposition; read the latest callbacks (zoom lock).
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val modifier = Modifier
        .onGloballyPositioned { pageCoordinates = it }
        .then(
            // While drawing, a finger is a pen: no page-turn or zoom taps.
            if (scribble.active) {
                Modifier
            } else {
                Modifier.pointerInput(index) {
                    detectTapGestures(
                        onDoubleTap = { currentOnDoubleTap() },
                        onTap = { offset -> currentOnTap(pageCoordinates?.localToRoot(offset)?.x ?: offset.x) },
                    )
                }
            },
        )

    if (image == null) {
        Box(
            modifier
                .fillMaxWidth()
                .aspectRatio(PLACEHOLDER_RATIO)
                .background(Color(0xFF121212)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    // The box has exactly the picture's shape, so ink normalized against it stays on the art.
    val ratio = (image.width.toFloat() / image.height.coerceAtLeast(1)).coerceAtLeast(0.1f)
    val box = when (fitMode) {
        ComicFitMode.WIDTH -> modifier.fillMaxWidth().aspectRatio(ratio)
        ComicFitMode.HEIGHT -> modifier.fillMaxHeight().aspectRatio(ratio, matchHeightConstraintsFirst = true)
        else -> modifier.fillMaxSize().wrapContentSize(Alignment.Center).aspectRatio(ratio)
    }
    Box(box) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        InkLayer(
            strokes = ink,
            scribble = scribble,
            crop = null,
            multiplyHighlighter = true,
            onStroke = onInkStroke,
            onErase = onInkErase,
            onEraseEnd = onInkEraseEnd,
        )
    }
}

private class ComicController(
    private val comic: ComicSource,
    private val spreads: List<IntRange>,
    private val direction: ComicDirection,
    private val pagerState: PagerState,
    private val listState: androidx.compose.foundation.lazy.LazyListState,
    private val scope: CoroutineScope,
    private val resetZoomAction: () -> Unit,
) : ReaderController {

    override fun resetZoom() = resetZoomAction()

    override val supportsPageThumbnails = true

    override fun goTo(location: BookLocation) {
        val page = (location.page ?: 0).coerceIn(0, (comic.pageCount - 1).coerceAtLeast(0))
        scope.launch {
            if (direction == ComicDirection.VERTICAL) {
                listState.scrollToItem(page)
            } else {
                pagerState.scrollToPage(spreads.indexOfFirst { page in it }.coerceAtLeast(0))
            }
        }
    }

    override fun nextPage() = step(1)

    override fun previousPage() = step(-1)

    private fun step(delta: Int) {
        scope.launch {
            if (direction == ComicDirection.VERTICAL) {
                listState.animateScrollToItem(
                    (listState.firstVisibleItemIndex + delta).coerceIn(0, (comic.pageCount - 1).coerceAtLeast(0)),
                )
            } else {
                pagerState.animateScrollToPage(
                    (pagerState.currentPage + delta).coerceIn(0, spreads.lastIndex.coerceAtLeast(0)),
                )
            }
        }
    }

    override suspend fun thumbnail(page: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        comic.pageBytes(page)?.let { ComicSources.decode(it, widthPx) }
    }
}

private fun ReaderHost.reportComic(pageCount: Int, page: Int, fraction: Double) {
    val progress = if (pageCount <= 0) 0f else ((page + fraction) / pageCount).toFloat().coerceIn(0f, 1f)
    onProgress(
        BookLocation(
            page = page,
            offset = fraction,
            totalProgression = progress.toDouble(),
            label = "${page + 1}",
        ),
        progress,
        page,
        pageCount,
        "${page + 1}",
    )
}

internal fun ComicSource.spreads(coverAlone: Boolean): List<IntRange> {
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

private const val PLACEHOLDER_RATIO = 0.7f

private class ComicPageCache(maxBytes: Int = defaultMaxBytes()) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    fun get(page: Int, width: Int): Bitmap? = cache.get("$page@$width")?.takeIf { !it.isRecycled }

    fun put(page: Int, width: Int, bitmap: Bitmap) {
        cache.put("$page@$width", bitmap)
    }

    fun clear() = cache.evictAll()

    companion object {
        fun defaultMaxBytes(): Int =
            (Runtime.getRuntime().maxMemory() / 5).coerceAtMost(96L * 1024 * 1024).toInt()
    }
}
