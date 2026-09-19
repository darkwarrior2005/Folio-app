package app.folio.ui.screens.reader

import app.folio.data.settings.effectiveTextSize
import app.folio.core.model.ZoomGate
import app.folio.ui.components.rememberNotificationPermissionRequest
import androidx.compose.foundation.border
import app.folio.ui.screens.notes.label
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.DrawnNoteProximity
import app.folio.core.model.HighlightColor
import app.folio.core.model.ReaderFamily
import app.folio.data.settings.ComicDirection
import app.folio.data.settings.PdfFitMode
import app.folio.data.settings.PdfViewMode
import app.folio.data.settings.ReaderScrollMode
import app.folio.data.settings.ReaderTextAlign
import app.folio.reader.ReaderFonts
import app.folio.reader.api.ReaderSelection
import app.folio.ui.LocalContainer
import app.folio.ui.util.Format
import kotlinx.coroutines.launch

enum class ReaderSheet { CONTENTS, SEARCH, BOOKMARKS, SETTINGS, POMODORO, NOTE, DRAWINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSheets(
    sheet: ReaderSheet?,
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit,
    onOpenDrawnNote: (Long) -> Unit = {},
) {
    if (sheet == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Box(Modifier.navigationBarsPadding()) {
            when (sheet) {
                ReaderSheet.CONTENTS -> ContentsSheet(viewModel, onDismiss)
                ReaderSheet.SEARCH -> SearchSheet(viewModel, onDismiss)
                ReaderSheet.BOOKMARKS -> BookmarksSheet(viewModel, onDismiss)
                ReaderSheet.SETTINGS -> ReaderSettingsSheet(viewModel)
                ReaderSheet.POMODORO -> PomodoroSheet(viewModel)
                ReaderSheet.NOTE -> NoteSheet(viewModel, onDismiss)
                ReaderSheet.DRAWINGS -> DrawingsSheet(viewModel, onDismiss, onOpenDrawnNote)
            }
        }
    }
}

@Composable
private fun ContentsSheet(viewModel: ReaderViewModel, onDismiss: () -> Unit) {
    val toc by viewModel.toc.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()

    Column(Modifier.heightIn(max = 520.dp)) {
        SheetTitle(stringResource(R.string.reader_contents))
        if (toc.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_history),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn {
                items(toc) { entry ->
                    val isCurrent = entry.location.page != null && entry.location.page == position.page
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.goTo(entry.location)
                                onDismiss()
                            }
                            .padding(
                                start = 24.dp + (entry.level * 16).dp,
                                end = 24.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSheet(viewModel: ReaderViewModel, onDismiss: () -> Unit) {
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(Modifier.heightIn(max = 560.dp)) {
        SheetTitle(stringResource(R.string.reader_search_in_book))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.search(query) }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        when {
            searching -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            results.isEmpty() && query.isNotBlank() -> Text(
                text = stringResource(R.string.reader_no_results),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> LazyColumn {
                items(results) { hit ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.goTo(hit.location)
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = hit.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = hit.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookmarksSheet(viewModel: ReaderViewModel, onDismiss: () -> Unit) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()

    Column(Modifier.heightIn(max = 520.dp)) {
        SheetTitle(stringResource(R.string.book_bookmarks))
        LazyColumn {
            items(bookmarks) { bookmark ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.locationOf(bookmark.location)?.let { viewModel.goTo(it) }
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(bookmark.title ?: bookmark.positionLabel, style = MaterialTheme.typography.bodyMedium)
                        bookmark.note?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
            if (highlights.isNotEmpty()) {
                item {
                    SheetTitle(stringResource(R.string.book_highlights))
                }
                items(highlights) { highlight ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.locationOf(highlight.location)?.let { viewModel.goTo(it) }
                                onDismiss()
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(highlight.color.argb)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = highlight.text,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawingsSheet(viewModel: ReaderViewModel, onDismiss: () -> Unit, onOpen: (Long) -> Unit) {
    val drawings by viewModel.drawnNotes.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val here = remember(drawings, position.progress) {
        DrawnNoteProximity.near(drawings, position.progress) { it.progress }.map { it.id }.toSet()
    }

    Column(Modifier.heightIn(max = 520.dp)) {
        SheetTitle(stringResource(R.string.drawings_title))
        if (drawings.isEmpty()) {
            Text(
                text = stringResource(R.string.drawings_empty),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn {
            items(drawings, key = { it.id }) { drawing ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpen(drawing.id)
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Gesture,
                        contentDescription = null,
                        tint = if (drawing.id in here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(drawing.positionLabel, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = app.folio.ui.util.Format.relativeDate(drawing.updatedAt).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.locationOf(drawing.location)?.let { viewModel.goTo(it) }
                            onDismiss()
                        },
                    ) {
                        Icon(Icons.Rounded.NearMe, contentDescription = stringResource(R.string.drawings_go_to))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderSettingsSheet(viewModel: ReaderViewModel) {
    val container = LocalContainer.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val family = (state as? ReaderUiState.Ready)?.engine?.family
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun update(transform: (app.folio.data.settings.AppSettings) -> app.folio.data.settings.AppSettings) {
        scope.launch { container.settings.update(transform) }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
    ) {
        SheetTitle(stringResource(R.string.reader_settings), padded = false)

        when (family) {
            ReaderFamily.EPUB, ReaderFamily.TEXT -> {
                val reflowable = settings.reflowable
                SettingLabel(stringResource(R.string.setting_page_theme))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reflowable.readerThemeId == null,
                        onClick = { update { it.copy(reflowable = it.reflowable.copy(readerThemeId = null)) } },
                        label = { Text(stringResource(R.string.setting_page_theme_app)) },
                    )
                    app.folio.ui.theme.FolioThemes.all.forEach { theme ->
                        FilterChip(
                            selected = reflowable.readerThemeId == theme.id,
                            onClick = { update { it.copy(reflowable = it.reflowable.copy(readerThemeId = theme.id)) } },
                            label = { Text(stringResource(theme.nameRes)) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(theme.readerBackground)
                                        .border(1.dp, theme.readerText.copy(alpha = 0.5f), CircleShape),
                                )
                            },
                        )
                    }
                }

                SettingLabel(stringResource(R.string.setting_font))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderFonts.all.forEach { font ->
                        FilterChip(
                            selected = reflowable.fontFamily == font.displayName,
                            onClick = {
                                update { it.copy(reflowable = it.reflowable.copy(fontFamily = font.displayName)) }
                            },
                            label = { Text(font.displayName) },
                        )
                    }
                }

                // Edits this book's size: a per-book size (for example from pinching) would
                // otherwise silently override a change to the global default.
                val bookPrefs by viewModel.bookPrefsState.collectAsStateWithLifecycle()
                val bookTextSize = bookPrefs.effectiveTextSize(settings)
                StepperRow(
                    label = stringResource(R.string.setting_font_size),
                    value = "$bookTextSize%",
                    onDecrease = {
                        viewModel.updateBookPrefs {
                            it.copy(textSizePercent = (bookTextSize - 10).coerceAtLeast(ZoomGate.MIN_TEXT))
                        }
                    },
                    onIncrease = {
                        viewModel.updateBookPrefs {
                            it.copy(textSizePercent = (bookTextSize + 10).coerceAtMost(ZoomGate.MAX_TEXT))
                        }
                    },
                )

                SliderRow(
                    label = stringResource(R.string.setting_line_height),
                    value = reflowable.lineHeight.toFloat(),
                    range = 1f..2.4f,
                    display = String.format("%.2f", reflowable.lineHeight),
                ) { value ->
                    update { it.copy(reflowable = it.reflowable.copy(lineHeight = value.toDouble(), publisherStyles = false)) }
                }

                SliderRow(
                    label = stringResource(R.string.setting_letter_spacing),
                    value = reflowable.letterSpacing.toFloat(),
                    range = 0f..0.25f,
                    display = String.format("%.2f", reflowable.letterSpacing),
                ) { value ->
                    update { it.copy(reflowable = it.reflowable.copy(letterSpacing = value.toDouble(), publisherStyles = false)) }
                }

                SliderRow(
                    label = stringResource(R.string.setting_paragraph_spacing),
                    value = reflowable.paragraphSpacing.toFloat(),
                    range = 0f..2f,
                    display = String.format("%.2f", reflowable.paragraphSpacing),
                ) { value ->
                    update { it.copy(reflowable = it.reflowable.copy(paragraphSpacing = value.toDouble(), publisherStyles = false)) }
                }

                StepperRow(
                    label = stringResource(R.string.setting_margins),
                    value = "${reflowable.pageMarginsPercent}%",
                    onDecrease = {
                        update {
                            it.copy(
                                reflowable = it.reflowable.copy(
                                    pageMarginsPercent = (it.reflowable.pageMarginsPercent - 10).coerceAtLeast(30),
                                ),
                            )
                        }
                    },
                    onIncrease = {
                        update {
                            it.copy(
                                reflowable = it.reflowable.copy(
                                    pageMarginsPercent = (it.reflowable.pageMarginsPercent + 10).coerceAtMost(300),
                                ),
                            )
                        }
                    },
                )

                SettingLabel(stringResource(R.string.setting_text_align))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTextAlign.entries.forEach { align ->
                        FilterChip(
                            selected = reflowable.textAlign == align,
                            onClick = { update { it.copy(reflowable = it.reflowable.copy(textAlign = align, publisherStyles = false)) } },
                            label = { Text(align.name.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                        )
                    }
                }

                SettingLabel(stringResource(R.string.setting_reading_mode))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = reflowable.scrollMode == ReaderScrollMode.PAGINATED,
                        onClick = {
                            update {
                                it.copy(reflowable = it.reflowable.copy(scrollMode = ReaderScrollMode.PAGINATED))
                            }
                        },
                        label = { Text(stringResource(R.string.setting_paginated)) },
                    )
                    FilterChip(
                        selected = reflowable.scrollMode == ReaderScrollMode.SCROLL,
                        onClick = {
                            update { it.copy(reflowable = it.reflowable.copy(scrollMode = ReaderScrollMode.SCROLL)) }
                        },
                        label = { Text(stringResource(R.string.setting_scroll)) },
                    )
                }

                SwitchRow(
                    label = stringResource(R.string.setting_publisher_styles),
                    checked = reflowable.publisherStyles,
                ) { checked ->
                    update { it.copy(reflowable = it.reflowable.copy(publisherStyles = checked)) }
                }
            }

            ReaderFamily.PDF -> {
                val pdf = settings.pdf
                SettingLabel(stringResource(R.string.setting_view_mode))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfViewMode.entries.forEach { mode ->
                        FilterChip(
                            selected = pdf.viewMode == mode,
                            onClick = { update { it.copy(pdf = it.pdf.copy(viewMode = mode)) } },
                            label = { Text(mode.label()) },
                        )
                    }
                }
                SettingLabel(stringResource(R.string.setting_fit))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfFitMode.entries.forEach { mode ->
                        FilterChip(
                            selected = pdf.fitMode == mode,
                            onClick = { update { it.copy(pdf = it.pdf.copy(fitMode = mode)) } },
                            label = {
                                Text(
                                    stringResource(
                                        if (mode == PdfFitMode.WIDTH) {
                                            R.string.setting_fit_width
                                        } else {
                                            R.string.setting_fit_page
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                SwitchRow(stringResource(R.string.setting_night_mode), pdf.nightMode) { checked ->
                    update { it.copy(pdf = it.pdf.copy(nightMode = checked)) }
                }
                SwitchRow(stringResource(R.string.setting_crop_margins), pdf.cropMargins) { checked ->
                    update { it.copy(pdf = it.pdf.copy(cropMargins = checked)) }
                }
            }

            ReaderFamily.COMIC -> {
                val comic = settings.comic
                SettingLabel(stringResource(R.string.setting_direction))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ComicDirection.entries.forEach { direction ->
                        FilterChip(
                            selected = (viewModelDirection(viewModel) ?: comic.direction) == direction,
                            onClick = { viewModel.updateBookPrefs { it.copy(comicDirection = direction) } },
                            label = {
                                Text(
                                    stringResource(
                                        when (direction) {
                                            ComicDirection.LTR -> R.string.setting_ltr
                                            ComicDirection.RTL -> R.string.setting_rtl
                                            ComicDirection.VERTICAL -> R.string.setting_vertical
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                SwitchRow(stringResource(R.string.setting_double_page), comic.doublePage) { checked ->
                    update { it.copy(comic = it.comic.copy(doublePage = checked)) }
                    viewModel.updateBookPrefs { it.copy(comicDoublePage = checked) }
                }
                SwitchRow(stringResource(R.string.setting_cover_alone), comic.coverAlone) { checked ->
                    update { it.copy(comic = it.comic.copy(coverAlone = checked)) }
                }
            }

            null -> Unit
        }

        SettingLabel(stringResource(R.string.settings_reader))
        SwitchRow(stringResource(R.string.setting_keep_screen_on), settings.behavior.keepScreenOn) { checked ->
            update { it.copy(behavior = it.behavior.copy(keepScreenOn = checked)) }
        }
        SwitchRow(stringResource(R.string.setting_fullscreen), settings.behavior.fullscreen) { checked ->
            update { it.copy(behavior = it.behavior.copy(fullscreen = checked)) }
        }
        SwitchRow(stringResource(R.string.setting_invert_tap), settings.behavior.invertTapZones) { checked ->
            update { it.copy(behavior = it.behavior.copy(invertTapZones = checked)) }
        }
    }
}

@Composable
private fun viewModelDirection(viewModel: ReaderViewModel): ComicDirection? {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val host = (state as? ReaderUiState.Ready)?.host ?: return null
    val prefs by host.bookPrefs.collectAsStateWithLifecycle()
    return prefs.comicDirection
}

@Composable
private fun PomodoroSheet(viewModel: ReaderViewModel) {
    val askNotifications = rememberNotificationPermissionRequest()
    val state by viewModel.pomodoro.collectAsStateWithLifecycle()
    val remaining by viewModel.pomodoroRemaining.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(
                when (state.phase) {
                    app.folio.data.db.PomodoroPhase.FOCUS -> R.string.pomodoro_focus
                    app.folio.data.db.PomodoroPhase.SHORT_BREAK -> R.string.pomodoro_short_break
                    app.folio.data.db.PomodoroPhase.LONG_BREAK -> R.string.pomodoro_long_break
                },
            ),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (state.active) Format.timer(remaining) else "--:--",
            style = MaterialTheme.typography.displayMedium,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!state.active) {
                TextButton(onClick = { askNotifications(); viewModel.startPomodoro() }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.pomodoro_start))
                }
            } else {
                TextButton(
                    onClick = { if (state.running) viewModel.pausePomodoro() else viewModel.resumePomodoro() },
                ) {
                    Text(stringResource(if (state.running) R.string.pomodoro_pause else R.string.pomodoro_resume))
                }
                TextButton(onClick = viewModel::stopPomodoro) {
                    Text(stringResource(R.string.pomodoro_stop))
                }
            }
        }
        Text(
            text = pluralStringResource(R.plurals.pomodoro_today_sessions, state.completedFocusSessions, state.completedFocusSessions),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoteSheet(viewModel: ReaderViewModel, onDismiss: () -> Unit) {
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    var note by remember { mutableStateOf("") }

    Column(Modifier.padding(24.dp)) {
        SheetTitle(stringResource(R.string.action_add_note), padded = false)
        selection?.text?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            TextButton(
                onClick = {
                    viewModel.noteForSelection(note)
                    onDismiss()
                },
                enabled = note.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
fun SelectionToolbar(
    selection: ReaderSelection,
    onHighlight: (HighlightColor) -> Unit,
    onNote: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    // Two rows so every action fits on narrow phones: colours on top, actions below.
    Column(
        modifier
            .padding(16.dp)
            .navigationBarsPadding()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HighlightColor.entries.forEach { color ->
                val label = color.label()
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = label) { onHighlight(color) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(color.argb)),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNote) {
                Icon(Icons.Rounded.NoteAdd, contentDescription = stringResource(R.string.action_add_note))
            }
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(selection.text))
                    onDismiss()
                },
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.action_copy))
            }
            IconButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, selection.text)
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(intent, null)) }
                    onDismiss()
                },
            ) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
            }
            if (selection.existingHighlightId != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
            }
        }
    }
}

@Composable
private fun SheetTitle(text: String, padded: Boolean = true) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = if (padded) {
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        } else {
            Modifier.padding(vertical = 12.dp)
        },
    )
}

@Composable
private fun SettingLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onDecrease) { Text("−") }
        Text(value, style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = onIncrease) { Text("+") }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(display, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), valueRange = range, onValueChange = onChange)
    }
}

@Composable
internal fun PdfViewMode.label(): String = stringResource(
    when (this) {
        PdfViewMode.CONTINUOUS -> R.string.setting_continuous
        PdfViewMode.PAGED_HORIZONTAL -> R.string.setting_paged_horizontal
        PdfViewMode.PAGED_VERTICAL -> R.string.setting_paged_vertical
        PdfViewMode.DOUBLE_PAGE -> R.string.setting_double_page
    },
)
