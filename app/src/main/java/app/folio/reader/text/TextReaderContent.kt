package app.folio.reader.text

import app.folio.reader.common.pinchGesture
import app.folio.reader.api.ZoomNotice
import app.folio.data.settings.effectiveTextSize
import app.folio.core.model.ZoomLimit
import app.folio.core.model.ZoomGate
import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.WebViewAssetLoader
import app.folio.R
import app.folio.core.model.BookLocation
import app.folio.core.model.InBookSearchHit
import app.folio.core.model.TextRange
import app.folio.data.db.HighlightEntity
import app.folio.data.files.FileAccess
import app.folio.data.settings.ReaderScrollMode
import app.folio.data.settings.ReaderTextAlign
import app.folio.reader.ReaderFonts
import app.folio.reader.api.BookOpenError
import app.folio.reader.api.ReaderController
import app.folio.reader.api.ReaderHost
import app.folio.reader.api.ReaderSelection
import app.folio.reader.pdf.TapAction
import app.folio.reader.pdf.tapZone
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val ASSET_BASE = "https://appassets.androidplatform.net/assets/"

@Composable
fun TextReaderContent(host: ReaderHost, files: FileAccess) {
    val context = LocalContext.current
    val settings by host.settings.collectAsStateWithLifecycle()
    val highlights by host.highlights.collectAsStateWithLifecycle()

    var document by remember { mutableStateOf<TextDocument?>(null) }
    var webView by remember { mutableStateOf<FolioWebView?>(null) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(host.source.uri) {
        TextDocument.load(files, host.source)
            .onSuccess { loaded ->
                document = loaded
                host.onToc(loaded.toc())
            }
            .onFailure { throwable ->
                host.onError((throwable as? TextOpenException)?.error ?: BookOpenError.Failed(throwable.message))
            }
    }

    val loaded = document
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val colors = MaterialTheme.colorScheme
    val (pageBackground, pageText) = app.folio.ui.theme.readerPageColors(settings)
    val bookPrefs by host.bookPrefs.collectAsStateWithLifecycle()
    val locked = bookPrefs.zoomLocked == true
    // While a pinch is in progress the text size follows the fingers; it is saved when the pinch ends.
    var liveTextSize by remember { mutableStateOf<Int?>(null) }
    val textSize = liveTextSize ?: bookPrefs.effectiveTextSize(settings)
    var gestureStartSize by remember { mutableStateOf(textSize) }
    var lastLimit by remember { mutableStateOf<ZoomLimit?>(null) }
    val settingsJson = remember(settings.reflowable, textSize, pageBackground, pageText, colors.primary) {
        buildSettingsJson(
            reflowable = settings.reflowable.copy(fontSizePercent = textSize),
            backgroundArgb = pageBackground.toArgb(),
            foregroundArgb = pageText.toArgb(),
            accentArgb = colors.primary.toArgb(),
        )
    }

    val highlightLabel = remember(context) { context.getString(R.string.action_highlight) }
    val noteLabel = remember(context) { context.getString(R.string.action_add_note) }
    // Captured by the gesture callbacks, which outlive this composition pass.
    val behavior by rememberUpdatedState(settings.behavior)
    val currentHighlights by rememberUpdatedState(highlights)

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .pinchGesture(
                key = locked,
                onStart = {
                    if (!locked) {
                        gestureStartSize = textSize
                        lastLimit = null
                        host.onZoomNotice(ZoomNotice.ReflowsToTextSize)
                    }
                },
                onPinch = { scale ->
                    val step = ZoomGate.textSize(gestureStartSize, scale, locked) ?: return@pinchGesture
                    if (step.percent != textSize) liveTextSize = step.percent
                    if (step.hitLimit != null && step.hitLimit != lastLimit) {
                        host.onZoomNotice(ZoomNotice.TextSizeLimit(step.hitLimit))
                    }
                    lastLimit = step.hitLimit
                },
                onEnd = {
                    liveTextSize?.let { size -> host.updateBookPrefs { it.copy(textSizePercent = size) } }
                    liveTextSize = null
                },
            ),
        factory = { ctx ->
            FolioWebView(ctx).apply {
                configure(
                    bridge = TextReaderBridge(
                        positionCallback = { block, fraction, progress ->
                            host.onProgress(
                                BookLocation(
                                    page = block,
                                    offset = fraction.toDouble(),
                                    totalProgression = progress.toDouble(),
                                    label = loaded.blocks.getOrNull(block)?.text?.take(40),
                                ),
                                progress,
                                block,
                                loaded.blocks.size,
                                "${(progress * 100).toInt()}%",
                            )
                        },
                        tapCallback = { fraction ->
                            when (tapZone(fraction, behavior.tapZones, behavior.invertTapZones)) {
                                TapAction.NEXT -> evaluateJavascript("folio.next()", null)
                                TapAction.PREVIOUS -> evaluateJavascript("folio.previous()", null)
                                TapAction.TOGGLE_UI -> host.onCenterTap()
                            }
                        },
                        highlightTapCallback = { id ->
                            currentHighlights.firstOrNull { it.id == id }?.let { highlight ->
                                host.onSelectionChanged(highlight.toSelection())
                            }
                        },
                        readyCallback = { ready = true },
                    ),
                    selectionLabels = highlightLabel to noteLabel,
                    onSelectionAction = {
                        readSelectionInfo { selection ->
                            if (selection != null) {
                                host.onSelectionChanged(selection.toReaderSelection(loaded))
                            }
                        }
                    },
                )
                loadDataWithBaseURL(ASSET_BASE, buildHtml(loaded), "text/html", "utf-8", null)
                webView = this
            }
        },
        onRelease = { view ->
            view.destroy()
            webView = null
        },
    )

    LaunchedEffect(ready, settingsJson, webView) {
        if (!ready) return@LaunchedEffect
        webView?.evaluateJavascript("folio.applySettings($settingsJson)", null)
    }

    // Restore the saved position once the document is laid out.
    LaunchedEffect(ready, webView, loaded) {
        if (!ready) return@LaunchedEffect
        val location = host.initialLocation ?: return@LaunchedEffect
        val block = location.page ?: 0
        webView?.evaluateJavascript("folio.goToBlock($block, ${location.offset})", null)
    }

    LaunchedEffect(ready, highlights, webView) {
        if (!ready) return@LaunchedEffect
        val payload = buildJsonArray {
            highlights.filter { it.page != null && it.rangeStart != null && it.rangeEnd != null }
                .forEach { highlight ->
                    add(
                        buildJsonObject {
                            put("id", highlight.id)
                            put("block", highlight.page ?: 0)
                            put("start", highlight.rangeStart ?: 0)
                            put("end", highlight.rangeEnd ?: 0)
                            put("color", String.format("#%06X", highlight.color.argb.toInt() and 0xFFFFFF))
                        },
                    )
                }
        }
        webView?.evaluateJavascript("folio.applyHighlights(${JsonPrimitive(payload.toString())})", null)
    }

    val controller = remember(webView, loaded) {
        webView?.let { view -> TextController(view, loaded) }
    }

    DisposableEffect(controller) {
        host.onControllerReady(controller)
        onDispose { host.onControllerReady(null) }
    }

    LaunchedEffect(controller) {
        val current = controller ?: return@LaunchedEffect
        host.jumps.collectLatest { location -> current.goTo(location) }
    }
}

private data class RawSelection(val block: Int, val start: Int, val end: Int, val text: String, val rect: Rect?)

private fun RawSelection.toReaderSelection(document: TextDocument): ReaderSelection = ReaderSelection(
    text = text,
    location = BookLocation(
        page = block,
        totalProgression = if (document.blocks.isEmpty()) 0.0 else block.toDouble() / document.blocks.size,
        label = document.blocks.getOrNull(block)?.text?.take(40),
    ),
    positionLabel = document.blocks.getOrNull(block)?.text?.take(40).orEmpty(),
    progress = if (document.blocks.isEmpty()) 0f else block.toFloat() / document.blocks.size,
    page = block,
    range = TextRange(start, end),
    anchor = rect,
)

private fun HighlightEntity.toSelection(): ReaderSelection = ReaderSelection(
    text = text,
    location = BookLocation(page = page, totalProgression = progress.toDouble()),
    positionLabel = positionLabel,
    progress = progress,
    page = page,
    range = if (rangeStart != null && rangeEnd != null) TextRange(rangeStart, rangeEnd) else null,
    existingHighlightId = id,
)

private class TextController(
    private val webView: WebView,
    private val document: TextDocument,
) : ReaderController {
    override val supportsTextSelection = true
    override val supportsSearch = true

    override fun goTo(location: BookLocation) {
        val block = location.page
        if (block != null) {
            webView.evaluateJavascript("folio.goToBlock($block, ${location.offset})", null)
        } else {
            webView.evaluateJavascript("folio.goToProgress(${location.totalProgression})", null)
        }
    }

    override fun nextPage() {
        webView.evaluateJavascript("folio.next()", null)
    }

    override fun previousPage() {
        webView.evaluateJavascript("folio.previous()", null)
    }

    override fun clearSelection() {
        webView.evaluateJavascript("folio.clearSelection()", null)
    }

    override suspend fun search(query: String): List<InBookSearchHit> {
        val needle = query.trim()
        if (needle.length < 2) return emptyList()
        val hits = mutableListOf<InBookSearchHit>()
        document.blocks.forEach { block ->
            var index = block.text.indexOf(needle, ignoreCase = true)
            while (index >= 0 && hits.size < 300) {
                val from = (index - 40).coerceAtLeast(0)
                val to = (index + needle.length + 60).coerceAtMost(block.text.length)
                hits += InBookSearchHit(
                    location = BookLocation(
                        page = block.index,
                        totalProgression = block.index.toDouble() / document.blocks.size.coerceAtLeast(1),
                    ),
                    snippet = block.text.substring(from, to).trim(),
                    label = "${block.index + 1}",
                    range = TextRange(index, index + needle.length),
                )
                index = block.text.indexOf(needle, index + needle.length, ignoreCase = true)
            }
        }
        return hits
    }
}

/** WebView subclass that adds Highlight and Note to the text selection menu. */
@SuppressLint("SetJavaScriptEnabled", "ViewConstructor")
class FolioWebView(context: Context) : WebView(context) {

    private var selectionLabels: Pair<String, String> = "Highlight" to "Note"
    private var onSelectionAction: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    fun configure(
        bridge: TextReaderBridge,
        selectionLabels: Pair<String, String>,
        onSelectionAction: () -> Unit,
    ) {
        this.selectionLabels = selectionLabels
        this.onSelectionAction = onSelectionAction
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.textZoom = 100
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        addJavascriptInterface(bridge, "FolioBridge")

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            /** The app is offline by design: never navigate away from the document. */
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
        }
    }

    fun readSelection(onResult: (String?) -> Unit) {
        evaluateJavascript("folio.selectionInfo()") { value ->
            main.post { onResult(value) }
        }
    }

    override fun startActionMode(callback: ActionMode.Callback): ActionMode =
        super.startActionMode(wrap(callback))

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode =
        super.startActionMode(wrap(callback), type)

    private fun wrap(callback: ActionMode.Callback): ActionMode.Callback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val handled = callback.onCreateActionMode(mode, menu)
            menu.add(0, MENU_HIGHLIGHT, 0, selectionLabels.first)
            menu.add(0, MENU_NOTE, 1, selectionLabels.second)
            return handled
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean =
            callback.onPrepareActionMode(mode, menu)

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean =
            when (item.itemId) {
                MENU_HIGHLIGHT, MENU_NOTE -> {
                    onSelectionAction?.invoke()
                    mode.finish()
                    true
                }
                else -> callback.onActionItemClicked(mode, item)
            }

        override fun onDestroyActionMode(mode: ActionMode) = callback.onDestroyActionMode(mode)
    }

    companion object {
        private const val MENU_HIGHLIGHT = 92001
        private const val MENU_NOTE = 92002
    }
}

/** JavaScript calls arrive on a background thread and are hopped to the main thread here. */
class TextReaderBridge(
    private val positionCallback: (Int, Float, Float) -> Unit,
    private val tapCallback: (Float) -> Unit,
    private val highlightTapCallback: (Long) -> Unit,
    private val readyCallback: (Int) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPosition(block: Int, fraction: Float, progress: Float) {
        main.post { positionCallback(block, fraction, progress) }
    }

    @JavascriptInterface
    fun onTap(xFraction: Float) {
        main.post { tapCallback(xFraction) }
    }

    @JavascriptInterface
    fun onHighlightTap(id: Long) {
        main.post { highlightTapCallback(id) }
    }

    @JavascriptInterface
    fun onReady(blockCount: Int) {
        main.post { readyCallback(blockCount) }
    }
}

private fun FolioWebView.readSelectionInfo(onSelection: (RawSelection?) -> Unit) {
    readSelection { raw -> onSelection(parseSelection(raw)) }
}

private val json = Json { ignoreUnknownKeys = true }

private fun parseSelection(raw: String?): RawSelection? {
    if (raw == null || raw == "null") return null
    return try {
        // evaluateJavascript returns the JS string as a JSON-encoded string.
        val inner = json.decodeFromString<String>(raw)
        val element = json.parseToJsonElement(inner)
        val obj = element as? kotlinx.serialization.json.JsonObject ?: return null
        fun number(key: String): Double? =
            (obj[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
        val rectObj = obj["rect"] as? kotlinx.serialization.json.JsonObject
        val rect = rectObj?.let {
            fun value(key: String) = (it[key] as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0f
            Rect(value("left"), value("top"), value("right"), value("bottom"))
        }
        RawSelection(
            block = number("block")?.toInt() ?: return null,
            start = number("start")?.toInt() ?: 0,
            end = number("end")?.toInt() ?: 0,
            text = (obj["text"] as? JsonPrimitive)?.content.orEmpty(),
            rect = rect,
        )
    } catch (e: Exception) {
        null
    }
}

private fun buildSettingsJson(
    reflowable: app.folio.data.settings.ReflowableSettings,
    backgroundArgb: Int,
    foregroundArgb: Int,
    accentArgb: Int,
): String {
    val font = ReaderFonts.byName(reflowable.fontFamily)
    return buildJsonObject {
        put("background", hex(backgroundArgb))
        put("foreground", hex(foregroundArgb))
        put("accent", hex(accentArgb))
        put("fontFamily", font.cssStack)
        put("fontSize", 18.0 * reflowable.fontSizePercent / 100.0)
        put("lineHeight", reflowable.lineHeight)
        put("letterSpacing", reflowable.letterSpacing)
        put("wordSpacing", reflowable.wordSpacing)
        put("paragraphSpacing", reflowable.paragraphSpacing)
        put("margin", 20.0 * reflowable.pageMarginsPercent / 100.0)
        put("maxWidth", if (reflowable.maxLineLength > 0) "${reflowable.maxLineLength}ch" else "40em")
        put("fontWeight", reflowable.fontWeight)
        put(
            "textAlign",
            when (reflowable.textAlign) {
                ReaderTextAlign.START -> "start"
                ReaderTextAlign.JUSTIFY -> "justify"
                ReaderTextAlign.CENTER -> "center"
                ReaderTextAlign.END -> "end"
            },
        )
        put("paginated", reflowable.scrollMode == ReaderScrollMode.PAGINATED)
    }.toString()
}

private fun hex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

private fun buildHtml(document: TextDocument): String {
    val fontFaces = ReaderFonts.bundled.joinToString("\n") { font ->
        buildString {
            append("@font-face{font-family:\"").append(font.displayName).append("\";")
            append("src:url(\"/assets/").append(font.assetPath).append("\");")
            append("font-weight:100 900;font-style:normal;font-display:swap;}")
            font.italicAssetPath?.let { italic ->
                append("@font-face{font-family:\"").append(font.displayName).append("\";")
                append("src:url(\"/assets/").append(italic).append("\");")
                append("font-weight:100 900;font-style:italic;font-display:swap;}")
            }
        }
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
        <link rel="stylesheet" href="/assets/reader/reader.css">
        <style>$fontFaces</style>
        </head>
        <body>
        <div id="content">${document.bodyHtml()}</div>
        <script src="/assets/reader/reader.js"></script>
        </body>
        </html>
    """.trimIndent()
}
