package app.folio.ui.screens.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.HighlightColor
import app.folio.reader.common.InkPreview
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.EmptyState
import app.folio.ui.components.FolioChip
import app.folio.ui.folioViewModel
import app.folio.ui.theme.LocalSpacing
import app.folio.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenLocation: (Long, String) -> Unit,
    onOpenDrawing: (Long) -> Unit,
    viewModel: NotesViewModel = folioViewModel { NotesViewModel(it) },
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val drawings by viewModel.drawings.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val colorFilter by viewModel.colorFilter.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    var tab by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    val export = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let { viewModel.exportAll(it.toString()) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
                actions = {
                    IconButton(onClick = { export.launch("folio-notes.md") }) {
                        Icon(
                            Icons.Rounded.FileDownload,
                            contentDescription = stringResource(R.string.action_export_notes),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("${stringResource(R.string.notes_tab_bookmarks)} (${bookmarks.size})") },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("${stringResource(R.string.notes_tab_highlights)} (${highlights.size})") },
                )
                Tab(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    text = { Text("${stringResource(R.string.notes_tab_notes)} (${notes.size})") },
                )
                Tab(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    text = { Text("${stringResource(R.string.notes_tab_drawings)} (${drawings.size})") },
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.notes_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenPadding, vertical = 8.dp),
            )

            if (tab == 1) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = spacing.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HighlightColor.entries.forEach { color ->
                        FolioChip(
                            label = color.label(),
                            selected = colorFilter == color,
                            tint = Color(color.argb),
                            onClick = { viewModel.toggleColor(color) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            when (tab) {
                0 -> if (bookmarks.isEmpty()) {
                    EmptyState(stringResource(R.string.empty_bookmarks), modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = spacing.screenPadding,
                            end = spacing.screenPadding,
                            bottom = 80.dp,
                        ),
                    ) {
                        items(bookmarks, key = { it.bookmark.id }) { entry ->
                            NoteCard(
                                bookTitle = entry.bookTitle,
                                text = entry.bookmark.title ?: entry.bookmark.positionLabel,
                                subtitle = entry.bookmark.note,
                                label = entry.bookmark.positionLabel,
                                date = entry.bookmark.createdAt,
                                onClick = { onOpenLocation(entry.bookmark.bookId, entry.bookmark.location) },
                                onDelete = { viewModel.deleteBookmark(entry.bookmark.id) },
                            )
                        }
                    }
                }

                1 -> if (highlights.isEmpty()) {
                    EmptyState(stringResource(R.string.empty_highlights), modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = spacing.screenPadding,
                            end = spacing.screenPadding,
                            bottom = 80.dp,
                        ),
                    ) {
                        items(highlights, key = { it.highlight.id }) { entry ->
                            NoteCard(
                                bookTitle = entry.bookTitle,
                                text = entry.highlight.text,
                                subtitle = entry.highlight.note,
                                label = entry.highlight.positionLabel,
                                date = entry.highlight.createdAt,
                                tint = Color(entry.highlight.color.argb),
                                onClick = { onOpenLocation(entry.highlight.bookId, entry.highlight.location) },
                                onDelete = { viewModel.deleteHighlight(entry.highlight.id) },
                            )
                        }
                    }
                }

                2 -> if (notes.isEmpty()) {
                    EmptyState(stringResource(R.string.empty_notes), modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = spacing.screenPadding,
                            end = spacing.screenPadding,
                            bottom = 80.dp,
                        ),
                    ) {
                        items(notes, key = { it.note.id }) { entry ->
                            NoteCard(
                                bookTitle = entry.bookTitle,
                                text = entry.note.text,
                                subtitle = null,
                                label = entry.note.positionLabel,
                                date = entry.note.updatedAt,
                                onClick = { onOpenLocation(entry.note.bookId, entry.note.location) },
                                onDelete = { viewModel.deleteNote(entry.note.id) },
                            )
                        }
                    }
                }

                else -> if (drawings.isEmpty()) {
                    EmptyState(stringResource(R.string.empty_drawings), modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = spacing.screenPadding,
                            end = spacing.screenPadding,
                            bottom = 80.dp,
                        ),
                    ) {
                        items(drawings, key = { it.drawing.note.id }) { entry ->
                            DrawingCard(
                                entry = entry,
                                onClick = { onOpenDrawing(entry.drawing.note.id) },
                                onOpenInBook = { onOpenLocation(entry.drawing.note.bookId, entry.drawing.note.location) },
                                onDelete = { pendingDelete = entry.drawing.note.id },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.drawing_delete_title),
            message = stringResource(R.string.drawing_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteDrawing(id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun NoteCard(
    bookTitle: String,
    text: String,
    subtitle: String?,
    label: String,
    date: Long,
    tint: Color? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        if (tint != null) {
            Box(
                Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tint),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = bookTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = listOfNotNull(label.takeIf { it.isNotBlank() }, Format.relativeDate(date))
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DrawingCard(entry: DrawingEntry, onClick: () -> Unit, onOpenInBook: () -> Unit, onDelete: () -> Unit) {
    val note = entry.drawing.note
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(64.dp)
                .aspectRatio(1f / note.aspect)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White),
        ) {
            InkPreview(entry.strokes)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.drawing.bookTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = listOf(note.positionLabel, Format.relativeDate(note.updatedAt)).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenInBook) {
            Icon(
                Icons.AutoMirrored.Rounded.MenuBook,
                contentDescription = stringResource(R.string.drawing_open_in_book),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun HighlightColor.label(): String = stringResource(
    when (this) {
        HighlightColor.YELLOW -> R.string.highlight_color_yellow
        HighlightColor.BLUE -> R.string.highlight_color_blue
        HighlightColor.GREEN -> R.string.highlight_color_green
        HighlightColor.RED -> R.string.highlight_color_red
        HighlightColor.PURPLE -> R.string.highlight_color_purple
    },
)
