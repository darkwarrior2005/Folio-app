package app.folio.ui.screens.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.BookFormat
import app.folio.core.model.LibraryBook
import app.folio.core.model.ReadingStatus
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.FolioChip
import app.folio.ui.components.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookActionSheet(
    book: LibraryBook,
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onDetails: () -> Unit,
) {
    val context = LocalContext.current
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    var showCollections by remember { mutableStateOf(false) }
    var showTagInput by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider()

            SheetAction(Icons.Rounded.MenuBook, stringResource(R.string.action_read), onClick = onOpen)
            SheetAction(Icons.Rounded.Info, stringResource(R.string.book_details), onClick = onDetails)
            SheetAction(
                icon = Icons.Rounded.Star,
                label = stringResource(if (book.favorite) R.string.action_unfavorite else R.string.action_favorite),
            ) {
                viewModel.toggleFavorite(book)
                onDismiss()
            }
            SheetAction(Icons.Rounded.Bookmark, stringResource(R.string.action_add_tag)) { showTagInput = true }
            SheetAction(Icons.Rounded.Folder, stringResource(R.string.action_add_to_collection)) {
                showCollections = true
            }
            SheetAction(Icons.Rounded.PlaylistAdd, stringResource(R.string.action_add_to_queue)) {
                viewModel.enqueue(book.id)
                onDismiss()
            }
            if (book.status == ReadingStatus.FINISHED) {
                SheetAction(Icons.Rounded.Undo, stringResource(R.string.action_mark_unread)) {
                    viewModel.setStatus(book.id, ReadingStatus.UNREAD)
                    onDismiss()
                }
            } else {
                SheetAction(Icons.Rounded.CheckCircle, stringResource(R.string.action_mark_finished)) {
                    viewModel.setStatus(book.id, ReadingStatus.FINISHED)
                    onDismiss()
                }
            }
            SheetAction(Icons.Rounded.Edit, stringResource(R.string.action_edit), onClick = onDetails)
            SheetAction(Icons.Rounded.Share, stringResource(R.string.action_share)) {
                shareBook(context, book)
                onDismiss()
            }
            HorizontalDivider()
            SheetAction(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.action_remove_from_library),
                destructive = true,
            ) { confirmRemove = true }
        }
    }

    if (showCollections) {
        PickerDialog(
            title = stringResource(R.string.action_add_to_collection),
            options = collections.map { it.collection.id to it.collection.name },
            onPick = { id ->
                viewModel.addToCollection(id, book.id)
                showCollections = false
                onDismiss()
            },
            onCreate = { name ->
                viewModel.createCollectionWith(name, listOf(book.id))
                showCollections = false
                onDismiss()
            },
            onDismiss = { showCollections = false },
        )
    }

    if (showTagInput) {
        TextInputDialog(
            title = stringResource(R.string.action_add_tag),
            label = stringResource(R.string.field_tags),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = { name ->
                viewModel.addTagToBook(book.id, name)
                showTagInput = false
                onDismiss()
            },
            onDismiss = { showTagInput = false },
        )
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_remove_from_library_title),
            message = stringResource(R.string.dialog_remove_from_library_message),
            confirmLabel = stringResource(R.string.action_remove),
            destructive = true,
            onConfirm = {
                viewModel.removeFromLibrary(book.id)
                confirmRemove = false
                onDismiss()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkActionSheet(viewModel: LibraryViewModel, onDismiss: () -> Unit) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    var showTagInput by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(false) }
    var showCategories by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.library_selected_count, selection.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            SheetAction(Icons.Rounded.Bookmark, stringResource(R.string.action_add_tag)) { showTagInput = true }
            SheetAction(Icons.Rounded.Folder, stringResource(R.string.action_add_to_collection)) {
                showCollections = true
            }
            SheetAction(Icons.Rounded.Edit, stringResource(R.string.filter_category)) { showCategories = true }
            SheetAction(Icons.Rounded.Star, stringResource(R.string.action_favorite)) {
                viewModel.setFavoriteForSelection(true)
                onDismiss()
            }
            SheetAction(Icons.Rounded.CheckCircle, stringResource(R.string.action_mark_finished)) {
                viewModel.setStatusForSelection(ReadingStatus.FINISHED)
                onDismiss()
            }
            SheetAction(Icons.Rounded.Undo, stringResource(R.string.action_mark_unread)) {
                viewModel.setStatusForSelection(ReadingStatus.UNREAD)
                onDismiss()
            }
            SheetAction(Icons.Rounded.PlaylistAdd, stringResource(R.string.action_add_to_queue)) {
                viewModel.queueSelection()
                onDismiss()
            }
            HorizontalDivider()
            SheetAction(
                icon = Icons.Rounded.Delete,
                label = stringResource(R.string.action_remove_from_library),
                destructive = true,
            ) { confirmRemove = true }
        }
    }

    if (showTagInput) {
        TextInputDialog(
            title = stringResource(R.string.action_add_tag),
            label = stringResource(R.string.field_tags),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = { name ->
                viewModel.addTagToSelection(name)
                showTagInput = false
                onDismiss()
            },
            onDismiss = { showTagInput = false },
        )
    }

    if (showCollections) {
        PickerDialog(
            title = stringResource(R.string.action_add_to_collection),
            options = collections.map { it.collection.id to it.collection.name },
            onPick = { id ->
                viewModel.addSelectionToCollection(id)
                showCollections = false
                onDismiss()
            },
            onCreate = { name ->
                viewModel.createCollectionWith(name, selection.toList())
                showCollections = false
                onDismiss()
            },
            onDismiss = { showCollections = false },
        )
    }

    if (showCategories) {
        PickerDialog(
            title = stringResource(R.string.filter_category),
            options = categories.map { it.category.id to it.category.name },
            onPick = { id ->
                viewModel.setCategoryForSelection(id)
                showCategories = false
                onDismiss()
            },
            onCreate = { name ->
                viewModel.createCategoryAndAssign(name, selection.toList())
                showCategories = false
                onDismiss()
            },
            onDismiss = { showCategories = false },
        )
    }

    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(R.string.dialog_remove_from_library_title),
            message = stringResource(R.string.dialog_remove_from_library_message),
            confirmLabel = stringResource(R.string.action_remove),
            destructive = true,
            onConfirm = {
                viewModel.removeSelectionFromLibrary()
                confirmRemove = false
                onDismiss()
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(
    viewModel: LibraryViewModel,
    onDismiss: () -> Unit,
    onOpenCollections: () -> Unit,
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val authors by viewModel.authors.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            FilterGroup(stringResource(R.string.filter_format)) {
                BookFormat.entries.filter { it != BookFormat.IMAGE }.forEach { format ->
                    FolioChip(
                        label = format.label,
                        selected = format in filter.formats,
                        onClick = { viewModel.toggleFormat(format) },
                    )
                }
            }

            FilterGroup(stringResource(R.string.filter_status)) {
                ReadingStatus.entries.forEach { status ->
                    FolioChip(
                        label = status.label(),
                        selected = status in filter.statuses,
                        onClick = { viewModel.toggleStatus(status) },
                    )
                }
                FolioChip(
                    label = stringResource(R.string.filter_favorites),
                    selected = filter.favoritesOnly,
                    onClick = { viewModel.toggleFavorites() },
                )
            }

            if (categories.isNotEmpty()) {
                FilterGroup(stringResource(R.string.filter_category)) {
                    categories.forEach { category ->
                        FolioChip(
                            label = "${category.category.name} (${category.bookCount})",
                            selected = category.category.id in filter.categoryIds,
                            onClick = { viewModel.toggleCategory(category.category.id) },
                        )
                    }
                }
            }

            if (collections.isNotEmpty()) {
                FilterGroup(stringResource(R.string.filter_collection)) {
                    collections.forEach { collection ->
                        FolioChip(
                            label = "${collection.collection.name} (${collection.bookCount})",
                            selected = collection.collection.id in filter.collectionIds,
                            onClick = { viewModel.toggleCollection(collection.collection.id) },
                        )
                    }
                }
            }

            if (tags.isNotEmpty()) {
                FilterGroup(stringResource(R.string.filter_tag)) {
                    tags.forEach { tag ->
                        FolioChip(
                            label = "${tag.tag.name} (${tag.bookCount})",
                            selected = tag.tag.id in filter.tagIds,
                            onClick = { viewModel.toggleTag(tag.tag.id) },
                        )
                    }
                }
            }

            if (authors.isNotEmpty()) {
                FilterGroup(stringResource(R.string.filter_author)) {
                    authors.take(30).forEach { author ->
                        FolioChip(
                            label = author,
                            selected = author in filter.authors,
                            onClick = { viewModel.toggleAuthor(author) },
                        )
                    }
                }
            }

            if (series.isNotEmpty()) {
                FilterGroup(stringResource(R.string.filter_series)) {
                    series.take(30).forEach { name ->
                        FolioChip(
                            label = name,
                            selected = name in filter.series,
                            onClick = { viewModel.toggleSeries(name) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.filter_clear)) }
                TextButton(onClick = onOpenCollections) { Text(stringResource(R.string.collections_title)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            content()
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = label,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun PickerDialog(
    title: String,
    options: List<Pair<Long, String>>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { (id, name) ->
                    ListItem(
                        headlineContent = { Text(name) },
                        modifier = Modifier.clickable { onPick(id) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.dialog_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(newName.trim()) }, enabled = newName.isNotBlank()) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ReadingStatus.label(): String = stringResource(
    when (this) {
        ReadingStatus.UNREAD -> R.string.status_unread
        ReadingStatus.READING -> R.string.status_reading
        ReadingStatus.FINISHED -> R.string.status_finished
        ReadingStatus.ON_HOLD -> R.string.status_on_hold
        ReadingStatus.ABANDONED -> R.string.status_abandoned
    },
)

/** Nothing leaves the device unless the user chooses an app to share with. */
private fun shareBook(context: Context, book: LibraryBook) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = book.format.mimeTypes.firstOrNull() ?: "application/octet-stream"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(book.uri))
        putExtra(Intent.EXTRA_TITLE, book.title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, book.title)) }
}
