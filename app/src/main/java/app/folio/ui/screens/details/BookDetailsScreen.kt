package app.folio.ui.screens.details

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.ReadingStatus
import app.folio.ui.components.BookCover
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.EmptyState
import app.folio.ui.components.ProgressBar
import app.folio.ui.components.SectionHeader
import app.folio.ui.components.TextInputDialog
import app.folio.ui.folioViewModel
import app.folio.ui.screens.library.PickerDialog
import app.folio.ui.screens.library.label
import app.folio.ui.theme.LocalSpacing
import app.folio.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailsScreen(
    bookId: Long,
    onBack: () -> Unit,
    onRead: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onOpenLocation: (Long, String) -> Unit,
    viewModel: BookDetailsViewModel = folioViewModel(key = "details-$bookId") {
        BookDetailsViewModel(it, bookId)
    },
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val entity by viewModel.entity.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val memberIds by viewModel.memberCollectionIds.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val queued by viewModel.queued.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val context = LocalContext.current

    var tab by remember { mutableIntStateOf(0) }
    var showCollections by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renameFile by remember { mutableStateOf(false) }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.setCoverFromImage(it.toString()) }
    }
    val locateFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.relocate(it.toString()) }
    }
    val exportNotes = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let { viewModel.exportNotes(it.toString()) } }

    val current = book

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.book_details)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(bookId) }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            imageVector = if (current?.favorite == true) {
                                Icons.Rounded.Star
                            } else {
                                Icons.Rounded.StarBorder
                            },
                            contentDescription = stringResource(R.string.action_favorite),
                            tint = if (current?.favorite == true) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) {
            Box(Modifier.padding(padding)) {}
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = spacing.screenPadding,
                end = spacing.screenPadding,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                Row {
                    Column {
                        BookCover(
                            title = current.title,
                            coverPath = current.coverPath,
                            format = current.format,
                            modifier = Modifier
                                .width(128.dp)
                                .aspectRatio(0.66f),
                        )
                        TextButton(onClick = { pickCover.launch(arrayOf("image/*")) }) {
                            Text(stringResource(R.string.action_change_cover))
                        }
                    }
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = current.title,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = current.author ?: stringResource(R.string.unknown_author),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        current.series?.let { series ->
                            Text(
                                text = series + (current.volume?.let { " #${formatVolume(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = buildString {
                                append(current.format.label)
                                append(" · ")
                                append(Format.fileSize(current.fileSize))
                                current.pageCount?.let { append(" · ").append(it).append(" pages") }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!available || current.missing) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.missing_file_badge),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(onClick = { locateFile.launch(arrayOf("*/*")) }) {
                                Text(stringResource(R.string.action_locate_file))
                            }
                        }
                    }
                }
            }

            item(key = "progress") {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.progress_percent, Format.percent(current.progress)),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = Format.relativeDate(current.lastOpenedAt)
                                ?: stringResource(R.string.never_opened),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(current.progress, Modifier.fillMaxWidth(), height = 6.dp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onRead(bookId) },
                            modifier = Modifier.weight(1f),
                            enabled = available,
                        ) {
                            Icon(Icons.Rounded.MenuBook, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (current.progress > 0f) R.string.action_resume else R.string.action_read,
                                ),
                            )
                        }
                        OutlinedButton(onClick = viewModel::toggleQueue) {
                            Icon(Icons.Rounded.PlaylistAdd, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(
                                    if (queued) R.string.action_remove_from_queue else R.string.action_add_to_queue,
                                ),
                            )
                        }
                    }
                }
            }

            item(key = "status") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadingStatus.entries.forEach { status ->
                        FilterChip(
                            selected = current.status == status,
                            onClick = { viewModel.setStatus(status) },
                            label = { Text(status.label()) },
                        )
                    }
                }
            }

            entity?.let { row ->
                val description = row.overrides.description ?: row.imported.description
                if (!description.isNullOrBlank()) {
                    item(key = "description") {
                        Column {
                            SectionHeader(stringResource(R.string.field_description))
                            Text(text = description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item(key = "organization") {
                Column {
                    SectionHeader(stringResource(R.string.field_tags))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        current.categoryName?.let { category ->
                            AssistChip(onClick = { onEdit(bookId) }, label = { Text(category) })
                        }
                        tags.forEach { tag ->
                            AssistChip(onClick = { onEdit(bookId) }, label = { Text(tag.name) })
                        }
                        AssistChip(
                            onClick = { onEdit(bookId) },
                            label = { Text(stringResource(R.string.action_add_tag)) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionHeader(stringResource(R.string.collections_title))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        collections.filter { it.collection.id in memberIds }.forEach { entry ->
                            AssistChip(
                                onClick = { viewModel.removeFromCollection(entry.collection.id) },
                                label = { Text(entry.collection.name) },
                                leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                            )
                        }
                        AssistChip(
                            onClick = { showCollections = true },
                            label = { Text(stringResource(R.string.action_add_to_collection)) },
                        )
                    }
                    if (entity?.format?.family?.let(app.folio.core.model.MusicSwitchPolicy::supports) == true) {
                        Spacer(Modifier.height(12.dp))
                        app.folio.ui.screens.music.BookMusicSection(bookId)
                    }
                }
            }

            item(key = "stats") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(spacing.cardPadding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Equal thirds, so the three labels never run into each other.
                        StatCell(stringResource(R.string.book_time_spent), Format.duration(context, stats.totalMs), Modifier.weight(1f))
                        StatCell(stringResource(R.string.book_sessions), stats.sessions.toString(), Modifier.weight(1f))
                        StatCell(stringResource(R.string.stats_pages_read), stats.pages.toString(), Modifier.weight(1f))
                    }
                }
            }

            item(key = "annotations") {
                Column {
                    TabRow(selectedTabIndex = tab) {
                        Tab(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            text = { Text("${stringResource(R.string.book_bookmarks)} (${bookmarks.size})") },
                        )
                        Tab(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            text = { Text("${stringResource(R.string.book_highlights)} (${highlights.size})") },
                        )
                        Tab(
                            selected = tab == 2,
                            onClick = { tab = 2 },
                            text = { Text("${stringResource(R.string.book_notes)} (${notes.size})") },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    when (tab) {
                        0 -> if (bookmarks.isEmpty()) {
                            EmptyState(stringResource(R.string.empty_bookmarks))
                        } else {
                            bookmarks.forEach { bookmark ->
                                AnnotationRow(
                                    title = bookmark.title ?: bookmark.positionLabel,
                                    subtitle = bookmark.note,
                                    label = bookmark.positionLabel,
                                    onClick = { onOpenLocation(bookId, bookmark.location) },
                                    onDelete = { viewModel.deleteBookmark(bookmark.id) },
                                )
                            }
                        }

                        1 -> if (highlights.isEmpty()) {
                            EmptyState(stringResource(R.string.empty_highlights))
                        } else {
                            highlights.forEach { highlight ->
                                AnnotationRow(
                                    title = highlight.text,
                                    subtitle = highlight.note,
                                    label = highlight.positionLabel,
                                    tint = androidx.compose.ui.graphics.Color(highlight.color.argb),
                                    onClick = { onOpenLocation(bookId, highlight.location) },
                                    onDelete = { viewModel.deleteHighlight(highlight.id) },
                                )
                            }
                        }

                        else -> if (notes.isEmpty()) {
                            EmptyState(stringResource(R.string.empty_notes))
                        } else {
                            notes.forEach { note ->
                                AnnotationRow(
                                    title = note.text,
                                    subtitle = null,
                                    label = note.positionLabel,
                                    onClick = { onOpenLocation(bookId, note.location) },
                                    onDelete = { viewModel.deleteNote(note.id) },
                                )
                            }
                        }
                    }
                    if (bookmarks.isNotEmpty() || highlights.isNotEmpty() || notes.isNotEmpty()) {
                        TextButton(onClick = { exportNotes.launch("${current.title}.md") }) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.action_export_notes))
                        }
                    }
                }
            }

            item(key = "file") {
                Column {
                    SectionHeader(stringResource(R.string.field_file_name))
                    Text(text = current.fileName, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { renameFile = true }) {
                            Text(stringResource(R.string.action_rename_file))
                        }
                        TextButton(onClick = viewModel::rescanMetadata) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_rescan))
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    TextButton(onClick = { confirmRemove = true }) {
                        Text(stringResource(R.string.action_remove_from_library))
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_delete_file),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showCollections) {
        PickerDialog(
            title = stringResource(R.string.action_add_to_collection),
            options = collections.map { it.collection.id to it.collection.name },
            onPick = {
                viewModel.addToCollection(it)
                showCollections = false
            },
            onCreate = {
                viewModel.createCollection(it)
                showCollections = false
            },
            onDismiss = { showCollections = false },
        )
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_remove_from_library_title),
            message = stringResource(R.string.dialog_remove_from_library_message),
            confirmLabel = stringResource(R.string.action_remove),
            destructive = true,
            onConfirm = {
                viewModel.removeFromLibrary()
                confirmRemove = false
                onBack()
            },
            onDismiss = { confirmRemove = false },
        )
    }

    if (confirmDelete && current != null) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_file_title),
            message = stringResource(R.string.dialog_delete_file_message, current.fileName),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteFile { success -> if (success) onBack() }
                confirmDelete = false
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (renameFile && current != null) {
        TextInputDialog(
            title = stringResource(R.string.dialog_rename_file_title),
            message = stringResource(R.string.dialog_rename_file_message),
            label = stringResource(R.string.field_file_name),
            initialValue = current.fileName,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { name ->
                viewModel.renameFile(name) { }
                renameFile = false
            },
            onDismiss = { renameFile = false },
        )
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AnnotationRow(
    title: String,
    subtitle: String?,
    label: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (tint != null) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .padding(end = 0.dp)
                    .background(tint, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(10.dp))
        } else {
            Icon(
                Icons.Rounded.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = label,
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

private fun formatVolume(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
