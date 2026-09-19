package app.folio.ui.screens.reader

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.folio.core.model.ZoomLimit
import app.folio.reader.api.ZoomNotice
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.DrawnNoteProximity
import app.folio.core.model.ReaderFamily
import app.folio.data.settings.OrientationLock
import app.folio.ui.components.EmptyState
import app.folio.ui.folioViewModel
import app.folio.ui.util.Format
import kotlinx.coroutines.delay

@Composable
fun ReaderScreen(
    bookId: Long,
    onBack: () -> Unit,
    startLocation: String? = null,
    onOpenDrawnNote: (Long) -> Unit = {},
    jumpTo: String? = null,
    onJumpHandled: () -> Unit = {},
    viewModel: ReaderViewModel = folioViewModel(key = "reader-$bookId") { ReaderViewModel(it, bookId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val controlsVisible by viewModel.controlsVisible.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val passwordRequired by viewModel.passwordRequired.collectAsStateWithLifecycle()
    val focusMode by viewModel.focusMode.collectAsStateWithLifecycle()
    val finishedPrompt by viewModel.finishedPrompt.collectAsStateWithLifecycle()
    val pomodoro by viewModel.pomodoro.collectAsStateWithLifecycle()
    val pomodoroRemaining by viewModel.pomodoroRemaining.collectAsStateWithLifecycle()
    val scribble by viewModel.scribble.collectAsStateWithLifecycle()
    val inkUndoRedo by viewModel.inkUndoRedo.collectAsStateWithLifecycle()
    val drawnNotes by viewModel.drawnNotes.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val bookPrefs by viewModel.bookPrefsState.collectAsStateWithLifecycle()
    val zoomLabel by viewModel.zoomLabel.collectAsStateWithLifecycle()
    val snackbarHost = remember { androidx.compose.material3.SnackbarHostState() }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val landscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(viewModel) {
        viewModel.zoomNotices.collect { notice ->
            val text = context.getString(
                when (notice) {
                    ZoomNotice.ReflowsToTextSize -> R.string.zoom_reflow_notice
                    ZoomNotice.OutOfMemory -> R.string.zoom_out_of_memory
                    is ZoomNotice.TextSizeLimit ->
                        if (notice.limit == ZoomLimit.MAX) R.string.zoom_text_max else R.string.zoom_text_min
                },
            )
            snackbarHost.currentSnackbarData?.dismiss()
            snackbarHost.showSnackbar(text)
        }
    }
    val container = app.folio.ui.LocalContainer.current
    LaunchedEffect(container) {
        container.musicPlayer.skippedMissing.collect { title ->
            snackbarHost.showSnackbar(context.getString(R.string.music_skipped_missing, title))
        }
    }
    LaunchedEffect(container) {
        container.bookMusic.notices.collect { res -> snackbarHost.showSnackbar(context.getString(res)) }
    }
    LaunchedEffect(viewModel) { viewModel.openDrawnNote.collect { onOpenDrawnNote(it) } }
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showGoToPage by remember { mutableStateOf(false) }

    // Jump straight to a bookmark or search hit the user came from.
    LaunchedEffect(uiState, startLocation) {
        if (uiState is ReaderUiState.Ready && startLocation != null) {
            viewModel.locationOf(startLocation)?.let { viewModel.goTo(it) }
        }
    }

    // Jump to a drawing's place when returning from its editor without navigating away.
    LaunchedEffect(jumpTo, uiState is ReaderUiState.Ready) {
        val raw = jumpTo ?: return@LaunchedEffect
        if (uiState is ReaderUiState.Ready) {
            viewModel.locationOf(raw)?.let { viewModel.goTo(it) }
            onJumpHandled()
        }
    }

    // Keep the screen awake and control system bars for an undistracted page.
    DisposableEffect(settings.behavior.keepScreenOn, activity) {
        val window = activity?.window
        if (settings.behavior.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    DisposableEffect(settings.behavior.orientationLock, activity) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = when (settings.behavior.orientationLock) {
            OrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            OrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { previous?.let { activity.requestedOrientation = it } }
    }

    DisposableEffect(controlsVisible, settings.behavior.fullscreen, focusMode, activity) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            val hide = settings.behavior.fullscreen && (!controlsVisible || focusMode)
            if (hide) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val w = activity?.window
            if (w != null) {
                WindowInsetsControllerCompat(w, w.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Save the position whenever the reader stops being visible.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // Leaving the screen (other app, screen off, back) closes the reading session, so
                // a force close later cannot lose the time already read.
                Lifecycle.Event.ON_STOP -> {
                    viewModel.flush()
                    viewModel.endSession()
                }
                Lifecycle.Event.ON_START -> viewModel.resumeSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Controls fade away on their own while reading.
    LaunchedEffect(controlsVisible, settings.behavior.autoHideControlsMs, sheet) {
        if (controlsVisible && sheet == null && settings.behavior.autoHideControlsMs > 0) {
            delay(settings.behavior.autoHideControlsMs.toLong())
            viewModel.setControlsVisible(false)
        }
    }

    BackHandler(enabled = selection != null || sheet != null || scribble.active) {
        when {
            sheet != null -> sheet = null
            scribble.active -> viewModel.stopScribble()
            else -> viewModel.clearSelection()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        when (val state = uiState) {
            is ReaderUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }

            is ReaderUiState.Failed -> EmptyState(
                title = stringResource(R.string.reader_error_title),
                message = stringResource(state.error.messageRes()),
                actionLabel = stringResource(R.string.action_back),
                onAction = onBack,
                modifier = Modifier.fillMaxSize(),
            )

            is ReaderUiState.Ready -> {
                state.engine.Content(state.host)

                val inkOnPages = state.engine.family == ReaderFamily.PDF || state.engine.family == ReaderFamily.COMIC
                val chromeVisible = controlsVisible && !focusMode && !scribble.active

                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter),
                ) {
                    ReaderTopBar(
                        title = state.title,
                        bookmarked = bookmarks.any { bookmark ->
                            position.page?.let { page ->
                                viewModel.locationOf(bookmark.location)?.page == page
                            } ?: (kotlin.math.abs(bookmark.progress - position.progress) < 0.001f)
                        },
                        onBack = {
                            viewModel.endSession()
                            onBack()
                        },
                        onToggleBookmark = viewModel::toggleBookmark,
                        onLongBookmark = { showBookmarkDialog = true },
                        onToc = { sheet = ReaderSheet.CONTENTS },
                        onSearch = { sheet = ReaderSheet.SEARCH },
                        onSettings = { sheet = ReaderSheet.SETTINGS },
                        onFocusMode = viewModel::toggleFocusMode,
                        onBookmarksList = { sheet = ReaderSheet.BOOKMARKS },
                    )
                }

                AnimatedVisibility(
                    visible = chromeVisible,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Row(
                            Modifier.padding(end = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!inkOnPages && drawnNotes.isNotEmpty()) {
                                val here = DrawnNoteProximity.near(drawnNotes, position.progress) { it.progress }.size
                                androidx.compose.material3.AssistChip(
                                    onClick = { sheet = ReaderSheet.DRAWINGS },
                                    label = {
                                        Text(
                                            if (here > 0) {
                                                stringResource(R.string.drawings_here, here)
                                            } else {
                                                stringResource(R.string.drawings_in_book, drawnNotes.size)
                                            },
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Gesture, contentDescription = null) },
                                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                    elevation = androidx.compose.material3.AssistChipDefaults.assistChipElevation(
                                        elevation = 3.dp,
                                    ),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            SmallFloatingActionButton(onClick = { viewModel.startScribble(inkOnPages) }) {
                                Icon(Icons.Rounded.Draw, contentDescription = stringResource(R.string.scribble_draw))
                            }
                        }
                        ReaderBottomBar(
                            position = position,
                            pomodoroLabel = if (pomodoro.active) Format.timer(pomodoroRemaining) else null,
                            onSeek = viewModel::seekTo,
                            onPrevious = { viewModel.previousPage() },
                            onNext = { viewModel.nextPage() },
                            onPomodoro = { sheet = ReaderSheet.POMODORO },
                            onGoToPage = { showGoToPage = true },
                            zoomLabel = zoomLabel,
                            onResetZoom = viewModel::resetZoom,
                        )
                    }
                }

                selection?.takeIf { !scribble.active }?.let { current ->
                    SelectionToolbar(
                        selection = current,
                        onHighlight = { color -> viewModel.highlightSelection(color) },
                        onNote = { sheet = ReaderSheet.NOTE },
                        onDelete = viewModel::deleteSelectedHighlight,
                        onDismiss = viewModel::clearSelection,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (scribble.active) {
                    ScribbleToolbar(
                        scribble = scribble,
                        undoRedo = inkUndoRedo,
                        onTool = viewModel::selectInkTool,
                        onStyle = viewModel::updateInkStyle,
                        onUndo = viewModel::undoInk,
                        onRedo = viewModel::redoInk,
                        onDone = viewModel::stopScribble,
                        pageLabel = position.pageLabel(),
                        onPrevious = { viewModel.previousPage() },
                        onNext = { viewModel.nextPage() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                val showLockButton = settings.behavior.zoomLockButton &&
                    (settings.behavior.zoomLockAlwaysVisible || (controlsVisible && !focusMode))
                if (showLockButton) {
                    ZoomLockButton(
                        locked = bookPrefs.zoomLocked == true,
                        offset = (if (landscape) bookPrefs.lockButtonLandscape else bookPrefs.lockButtonPortrait)
                            ?: app.folio.core.model.ZoomLockPlacement.DEFAULT,
                        onToggle = viewModel::toggleZoomLock,
                        onMoved = { viewModel.moveZoomLockButton(landscape, it) },
                    )
                }

                androidx.compose.material3.SnackbarHost(
                    snackbarHost,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                        .padding(bottom = 120.dp),
                )

                if (focusMode) {
                    // A single small affordance so focus mode is never a trap.
                    IconButton(
                        onClick = viewModel::toggleFocusMode,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.VisibilityOff,
                            contentDescription = stringResource(R.string.reader_focus_mode),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }

    ReaderSheets(
        sheet = sheet,
        viewModel = viewModel,
        onDismiss = { sheet = null },
        onOpenDrawnNote = onOpenDrawnNote,
    )

    if (showGoToPage) {
        val pageCount = position.pageCount ?: 0
        var input by remember { mutableStateOf("") }
        val target = input.toIntOrNull()
        val valid = target != null && target in 1..pageCount
        AlertDialog(
            onDismissRequest = { showGoToPage = false },
            title = { Text(stringResource(R.string.reader_go_to_page)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { value -> input = value.filter(Char::isDigit).take(6) },
                    label = { Text(stringResource(R.string.reader_go_to_page_hint, pageCount)) },
                    isError = input.isNotEmpty() && !valid,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        viewModel.goToPage(target!! - 1)
                        showGoToPage = false
                    },
                ) { Text(stringResource(R.string.action_open)) }
            },
            dismissButton = {
                TextButton(onClick = { showGoToPage = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showBookmarkDialog) {
        var title by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showBookmarkDialog = false },
            title = { Text(stringResource(R.string.action_add_bookmark)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.field_title)) },
                        singleLine = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.action_add_note)) },
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addBookmarkWithNote(title, note)
                        showBookmarkDialog = false
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarkDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (passwordRequired) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.dialog_password_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_password_message))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.submitPassword(password) }) {
                    Text(stringResource(R.string.action_open))
                }
            },
            dismissButton = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    if (finishedPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFinishedPrompt,
            title = { Text(stringResource(R.string.reader_finished_prompt)) },
            confirmButton = {
                TextButton(onClick = viewModel::markFinished) {
                    Text(stringResource(R.string.action_mark_finished))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissFinishedPrompt) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    bookmarked: Boolean,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onLongBookmark: () -> Unit,
    onToc: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onFocusMode: () -> Unit,
    onBookmarksList: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // Tap toggles a bookmark; long-press adds one with a title and note.
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = onToggleBookmark,
                    onLongClick = onLongBookmark,
                    onLongClickLabel = stringResource(R.string.action_add_bookmark),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = stringResource(R.string.action_add_bookmark),
                tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToc) {
            Icon(
                Icons.AutoMirrored.Rounded.List,
                contentDescription = stringResource(R.string.reader_contents),
            )
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.reader_settings))
        }
        IconButton(onClick = onFocusMode) {
            Icon(
                Icons.Rounded.VisibilityOff,
                contentDescription = stringResource(R.string.reader_focus_mode),
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    position: ReaderPosition,
    pomodoroLabel: String?,
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPomodoro: () -> Unit,
    onGoToPage: () -> Unit,
    zoomLabel: String?,
    onResetZoom: () -> Unit,
) {
    val zoomResetLabel = stringResource(R.string.zoom_reset)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        app.folio.ui.screens.music.NowPlayingBar(Modifier.padding(bottom = 4.dp))
        Slider(
            value = position.progress.coerceIn(0f, 1f),
            onValueChange = onSeek,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = null)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (zoomLabel != null) {
                    androidx.compose.material3.AssistChip(
                        onClick = onResetZoom,
                        label = { Text(zoomLabel) },
                        modifier = Modifier.semantics { contentDescription = zoomResetLabel },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                val canJump = (position.pageCount ?: 0) > 0
                Text(
                    text = position.pageLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = canJump, onClick = onGoToPage)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onPomodoro) {
                    Icon(Icons.Rounded.Timer, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(pomodoroLabel ?: stringResource(R.string.pomodoro_title))
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ReaderPosition.pageLabel(): String {
    val count = pageCount
    val page = page
    return when {
        count != null && count > 0 && page != null -> stringResource(R.string.reader_page_of, page + 1, count)
        label.isNotBlank() -> label
        else -> stringResource(R.string.reader_percent, Format.percent(progress))
    }
}

internal fun app.folio.reader.api.BookOpenError.messageRes(): Int = when (this) {
    app.folio.reader.api.BookOpenError.FileMissing -> R.string.error_file_missing
    app.folio.reader.api.BookOpenError.PermissionDenied -> R.string.error_permission
    app.folio.reader.api.BookOpenError.Unsupported -> R.string.error_unsupported
    app.folio.reader.api.BookOpenError.Empty -> R.string.error_empty
    app.folio.reader.api.BookOpenError.PasswordRequired -> R.string.dialog_password_title
    app.folio.reader.api.BookOpenError.WrongPassword -> R.string.dialog_password_wrong
    is app.folio.reader.api.BookOpenError.Corrupted -> R.string.error_corrupted
    is app.folio.reader.api.BookOpenError.Failed -> R.string.error_generic
}

internal fun android.content.Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
