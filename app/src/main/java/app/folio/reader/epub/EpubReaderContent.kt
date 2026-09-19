package app.folio.reader.epub

import org.readium.r2.shared.publication.Layout
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import app.folio.reader.common.magnifyGestures
import app.folio.reader.common.pinchGesture
import app.folio.reader.api.ZoomNotice
import app.folio.data.settings.effectiveTextSize
import app.folio.core.model.ZoomLimit
import app.folio.core.model.ZoomGate
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.compose.AndroidFragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.BookLocation
import app.folio.core.model.InBookSearchHit
import app.folio.data.db.HighlightEntity
import app.folio.data.db.LocationCodec
import app.folio.data.settings.ReaderScrollMode
import app.folio.data.settings.ReaderTextAlign
import app.folio.data.settings.ReflowableSettings
import app.folio.reader.ReaderFonts
import app.folio.reader.api.ReaderController
import app.folio.reader.api.ReaderHost
import app.folio.reader.api.ReaderSelection
import app.folio.reader.pdf.TapAction
import app.folio.reader.pdf.tapZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color as ReadiumColor
import org.readium.r2.navigator.preferences.ColumnCount
import org.readium.r2.navigator.preferences.FontFamily as ReadiumFontFamily
import org.readium.r2.navigator.preferences.TextAlign as ReadiumTextAlign
import org.readium.r2.navigator.preferences.Theme as ReadiumTheme
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.AbsoluteUrl

private const val DECORATION_GROUP = "highlights"
private const val MAX_SEARCH_HITS = 300
private const val MENU_HIGHLIGHT = 91001
private const val MENU_NOTE = 91002

@Composable
fun EpubReaderContent(host: ReaderHost, readium: ReadiumStack) {
    val context = LocalContext.current
    val settings by host.settings.collectAsStateWithLifecycle()
    val highlights by host.highlights.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var publication by remember { mutableStateOf<Publication?>(null) }
    var fragment by remember { mutableStateOf<EpubNavigatorFragment?>(null) }

    LaunchedEffect(host.source.uri) {
        readium.open(host.source.uri)
            .onSuccess { opened ->
                publication = opened
                host.onToc(opened.tocEntries())
            }
            .onFailure { throwable ->
                host.onError(
                    (throwable as? EpubOpenException)?.error
                        ?: app.folio.reader.api.BookOpenError.Failed(throwable.message),
                )
            }
    }

    DisposableEffect(publication) {
        val opened = publication
        onDispose { opened?.close() }
    }

    val loaded = publication
    if (loaded == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val bookPrefs by host.bookPrefs.collectAsStateWithLifecycle()
    val locked = bookPrefs.zoomLocked == true
    // While a pinch is in progress the text size follows the fingers; it is saved when the pinch ends.
    var liveTextSize by remember { mutableStateOf<Int?>(null) }
    val textSize = liveTextSize ?: bookPrefs.effectiveTextSize(settings)
    val (pageBackground, pageText) = app.folio.ui.theme.readerPageColors(settings)
    val preferences = remember(settings.reflowable, textSize, pageBackground, pageText) {
        settings.reflowable.copy(fontSizePercent = textSize).toEpubPreferences(
            backgroundArgb = pageBackground.toArgb(),
            textArgb = pageText.toArgb(),
        )
    }

    val selectionLabels = remember(context) {
        SelectionLabels(
            highlight = context.getString(R.string.action_highlight),
            note = context.getString(R.string.action_add_note),
        )
    }

    // Selection actions are added to the system text selection menu inside the WebView.
    val selectionCallback = remember(loaded) {
        SelectionActionModeCallback(selectionLabels) { action ->
            val current = fragment ?: return@SelectionActionModeCallback
            scope.launch {
                val selection = current.currentSelection() ?: return@launch
                host.onSelectionChanged(
                    ReaderSelection(
                        text = selection.locator.text.highlight.orEmpty().ifBlank {
                            selection.locator.title.orEmpty()
                        },
                        location = selection.locator.toBookLocation(),
                        positionLabel = selection.locator.title.orEmpty(),
                        progress = (selection.locator.locations.totalProgression ?: 0.0).toFloat(),
                        page = null,
                        range = null,
                        anchor = null,
                    ),
                )
                if (action == SelectionAction.NOTE) {
                    // The reader screen opens the note editor when a selection arrives with text.
                    current.clearSelection()
                }
            }
        }
    }

    val navigatorFactory = remember(loaded) { EpubNavigatorFactory(loaded) }
    val initialLocator = remember(loaded) { host.initialLocation?.toReadiumLocator() }

    DisposableEffect(navigatorFactory, initialLocator, selectionCallback) {
        ReaderFragmentFactory.delegate = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = preferences,
            listener = object : EpubNavigatorFragment.Listener {
                override fun onExternalLinkActivated(url: AbsoluteUrl) {
                    // Offline app: external links are ignored rather than opened.
                }
            },
            configuration = EpubNavigatorFragment.Configuration {
                servedAssets = listOf("fonts/.*")
                selectionActionModeCallback = selectionCallback
                ReaderFonts.bundled.forEach { font ->
                    addFontFamilyDeclaration(ReadiumFontFamily(font.displayName)) {
                        addFontFace {
                            addSource(font.assetPath!!)
                            setFontStyle(org.readium.r2.navigator.epub.css.FontStyle.NORMAL)
                            setFontWeight(100..900)
                        }
                        font.italicAssetPath?.let { italic ->
                            addFontFace {
                                addSource(italic)
                                setFontStyle(org.readium.r2.navigator.epub.css.FontStyle.ITALIC)
                                setFontWeight(100..900)
                            }
                        }
                    }
                }
            },
        )
        onDispose { ReaderFragmentFactory.delegate = null }
    }

    val fixedLayout = remember(loaded) { loaded.metadata.layout == Layout.FIXED }
    var gestureStartSize by remember { mutableStateOf(textSize) }
    var lastLimit by remember { mutableStateOf<ZoomLimit?>(null) }
    var fxlZoom by remember(loaded) { mutableStateOf(bookPrefs.zoom ?: 1f) }
    var fxlPan by remember(loaded) { mutableStateOf(Offset.Zero) }

    val gestureModifier = if (fixedLayout) {
        Modifier.magnifyGestures(
            key = loaded to locked,
            zoom = { fxlZoom },
            onZoom = { factor, _ ->
                val step = ZoomGate.magnify(fxlZoom, factor, locked) ?: return@magnifyGestures
                fxlZoom = step.zoom
                if (step.zoom <= 1f) fxlPan = Offset.Zero
            },
            onPan = { delta -> if (fxlZoom > 1f) fxlPan += delta },
            onEnd = {
                if (!locked) {
                    host.updateBookPrefs { it.copy(zoom = fxlZoom) }
                    host.onZoomLabel(if (fxlZoom > 1.01f) "${(fxlZoom * 100).roundToInt()}%" else null)
                }
            },
        )
    } else {
        Modifier.pinchGesture(
            key = loaded to locked,
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
        )
    }

    key(loaded) {
        AndroidFragment<EpubNavigatorFragment>(
            modifier = Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .graphicsLayer {
                    scaleX = fxlZoom
                    scaleY = fxlZoom
                    translationX = fxlPan.x
                    translationY = fxlPan.y
                },
        ) { created ->
            fragment = created
        }
    }

    val navigator = fragment

    LaunchedEffect(navigator, preferences) {
        navigator?.submitPreferences(preferences)
    }

    LaunchedEffect(navigator) {
        val current = navigator ?: return@LaunchedEffect
        current.currentLocator.collect { locator ->
            host.onProgress(
                locator.toBookLocation(),
                (locator.locations.totalProgression ?: 0.0).toFloat(),
                locator.locations.position,
                loaded.metadata.numberOfPages,
                locator.title ?: "",
            )
        }
    }

    DisposableEffect(navigator, settings.behavior.tapZones, settings.behavior.invertTapZones) {
        val current = navigator
        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val width = current?.view?.width?.toFloat() ?: return false
                if (width <= 0f) return false
                return when (tapZone(event.point.x / width, settings.behavior.tapZones, settings.behavior.invertTapZones)) {
                    TapAction.NEXT -> current.goForward(animated = true)
                    TapAction.PREVIOUS -> current.goBackward(animated = true)
                    TapAction.TOGGLE_UI -> {
                        host.onCenterTap()
                        true
                    }
                }
            }
        }
        current?.addInputListener(listener)
        onDispose { current?.removeInputListener(listener) }
    }

    LaunchedEffect(navigator, highlights) {
        val current = navigator ?: return@LaunchedEffect
        current.applyDecorations(highlights.mapNotNull { it.toDecoration() }, DECORATION_GROUP)
    }

    DisposableEffect(navigator, highlights) {
        val current = navigator
        val listener = object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val id = event.decoration.id.toLongOrNull() ?: return false
                val highlight = highlights.firstOrNull { it.id == id } ?: return false
                host.onSelectionChanged(
                    ReaderSelection(
                        text = highlight.text,
                        location = LocationCodec.decode(highlight.location) ?: BookLocation(),
                        positionLabel = highlight.positionLabel,
                        progress = highlight.progress,
                        existingHighlightId = highlight.id,
                    ),
                )
                return true
            }
        }
        current?.addDecorationListener(DECORATION_GROUP, listener)
        onDispose { current?.removeDecorationListener(listener) }
    }

    val controller = remember(navigator, loaded) {
        navigator?.let { EpubController(it, loaded, scope) }
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

private class EpubController(
    private val navigator: EpubNavigatorFragment,
    private val publication: Publication,
    private val scope: CoroutineScope,
) : ReaderController {
    override val supportsTextSelection = true
    override val supportsSearch = true

    override fun goTo(location: BookLocation) {
        val locator = location.toReadiumLocator()
        if (locator != null) {
            navigator.go(locator, animated = false)
        } else {
            scope.launch {
                publication.locateProgression(location.totalProgression)?.let { navigator.go(it) }
            }
        }
    }

    override fun nextPage() {
        navigator.goForward(animated = true)
    }

    override fun previousPage() {
        navigator.goBackward(animated = true)
    }

    override fun clearSelection() {
        navigator.clearSelection()
    }

    override suspend fun search(query: String): List<InBookSearchHit> {
        val iterator = publication.search(query) ?: return emptyList()
        val hits = mutableListOf<InBookSearchHit>()
        try {
            iterator.forEach { collection ->
                collection.locators.forEach { locator ->
                    if (hits.size < MAX_SEARCH_HITS) {
                        val text = locator.text
                        hits += InBookSearchHit(
                            location = locator.toBookLocation(),
                            snippet = buildString {
                                append(text.before.orEmpty().takeLast(45))
                                append(text.highlight.orEmpty())
                                append(text.after.orEmpty().take(60))
                            }.replace('\n', ' ').trim(),
                            label = locator.title.orEmpty(),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Partial results are better than none.
        } finally {
            iterator.close()
        }
        return hits
    }
}

private fun HighlightEntity.toDecoration(): Decoration? {
    val locator = LocationCodec.decode(location)?.toReadiumLocator() ?: return null
    return Decoration(
        id = id.toString(),
        locator = locator,
        style = Decoration.Style.Highlight(tint = color.argb.toInt(), isActive = false),
    )
}

internal fun ReflowableSettings.toEpubPreferences(backgroundArgb: Int, textArgb: Int): EpubPreferences {
    val font = ReaderFonts.byName(fontFamily)
    return EpubPreferences(
        backgroundColor = ReadiumColor(backgroundArgb),
        textColor = ReadiumColor(textArgb),
        theme = if (isDark(backgroundArgb)) ReadiumTheme.DARK else ReadiumTheme.LIGHT,
        fontFamily = ReadiumFontFamily(font.displayName),
        fontSize = fontSizePercent / 100.0,
        fontWeight = fontWeight / 400.0,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        wordSpacing = wordSpacing,
        paragraphSpacing = paragraphSpacing,
        pageMargins = pageMarginsPercent / 100.0,
        textAlign = when (textAlign) {
            ReaderTextAlign.START -> ReadiumTextAlign.START
            ReaderTextAlign.JUSTIFY -> ReadiumTextAlign.JUSTIFY
            ReaderTextAlign.CENTER -> ReadiumTextAlign.CENTER
            ReaderTextAlign.END -> ReadiumTextAlign.END
        },
        scroll = scrollMode == ReaderScrollMode.SCROLL,
        columnCount = when (columnCount) {
            1 -> ColumnCount.ONE
            2 -> ColumnCount.TWO
            else -> ColumnCount.AUTO
        },
        hyphens = hyphens,
        publisherStyles = publisherStyles,
    )
}

private fun isDark(argb: Int): Boolean {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return (r * 299 + g * 587 + b * 114) / 1000 < 128
}

private enum class SelectionAction { HIGHLIGHT, NOTE }

private data class SelectionLabels(val highlight: String, val note: String)

private class SelectionActionModeCallback(
    private val labels: SelectionLabels,
    private val onAction: (SelectionAction) -> Unit,
) : ActionMode.Callback {

    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.add(0, MENU_HIGHLIGHT, 0, labels.highlight)
        menu.add(0, MENU_NOTE, 1, labels.note)
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = when (item.itemId) {
        MENU_HIGHLIGHT -> {
            onAction(SelectionAction.HIGHLIGHT)
            mode.finish()
            true
        }
        MENU_NOTE -> {
            onAction(SelectionAction.NOTE)
            mode.finish()
            true
        }
        else -> false
    }

    override fun onDestroyActionMode(mode: ActionMode) = Unit
}
