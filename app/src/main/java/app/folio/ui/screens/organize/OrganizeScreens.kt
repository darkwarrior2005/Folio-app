package app.folio.ui.screens.organize

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.LibraryBook
import app.folio.core.model.SmartCollection
import app.folio.ui.components.BookCover
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.EmptyState
import app.folio.ui.components.ReorderableColumn
import app.folio.ui.components.SectionHeader
import app.folio.ui.components.TextInputDialog
import app.folio.ui.folioViewModel
import app.folio.ui.screens.library.label
import app.folio.ui.theme.LocalSpacing
import app.folio.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CollectionsScreen(
    onBack: () -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenSmart: (SmartCollection) -> Unit,
    onCategories: () -> Unit,
    onTags: () -> Unit,
    onQueue: () -> Unit,
    onTrash: () -> Unit,
    viewModel: CollectionsViewModel = folioViewModel { CollectionsViewModel(it) },
) {
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val smartCounts by viewModel.smartCounts.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Long?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collections_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_create))
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding)
                .padding(bottom = 96.dp),
        ) {
            SectionHeader(stringResource(R.string.collections_smart))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SmartCollection.entries.forEach { smart ->
                    AssistChip(
                        onClick = { onOpenSmart(smart) },
                        label = { Text("${smart.label()} (${smartCounts[smart] ?: 0})") },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionHeader(stringResource(R.string.collections_yours))
            if (collections.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.empty_collections_title),
                    message = stringResource(R.string.empty_collections_message),
                    actionLabel = stringResource(R.string.action_create),
                    onAction = { showCreate = true },
                )
            } else {
                collections.forEach { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpenCollection(entry.collection.id) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.collection.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = pluralStringResource(R.plurals.library_books_count, entry.bookCount, entry.bookCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { renaming = entry.collection.id }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_rename))
                        }
                        IconButton(onClick = { deleting = entry.collection.id }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onCategories) { Text(stringResource(R.string.categories_title)) }
                TextButton(onClick = onTags) { Text(stringResource(R.string.tags_title)) }
                TextButton(onClick = onQueue) { Text(stringResource(R.string.queue_title)) }
                TextButton(onClick = onTrash) { Text(stringResource(R.string.trash_title)) }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = stringResource(R.string.dialog_new_collection),
            label = stringResource(R.string.dialog_name),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = {
                viewModel.create(it)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }

    renaming?.let { id ->
        val entry = collections.firstOrNull { it.collection.id == id } ?: return@let
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.dialog_name),
            initialValue = entry.collection.name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                viewModel.rename(entry.collection, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            message = stringResource(R.string.dialog_delete_category_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    viewModel: CollectionDetailViewModel = folioViewModel(key = "collection-$collectionId") {
        CollectionDetailViewModel(it, collectionId)
    },
) {
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    var renaming by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection?.name ?: stringResource(R.string.collections_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_rename))
                    }
                },
            )
        },
    ) { padding ->
        if (books.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.collection_empty),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding),
        ) {
            ReorderableColumn(
                items = books,
                key = { it.id },
                onMove = viewModel::move,
            ) { book, index, handle ->
                ReorderRow(
                    book = book,
                    index = index,
                    handle = handle,
                    onClick = { onOpenBook(book.id) },
                    onRemove = { viewModel.remove(book.id) },
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.dialog_name),
            initialValue = collection?.name.orEmpty(),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    viewModel: QueueViewModel = folioViewModel { QueueViewModel(it) },
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.queue_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (books.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_queue),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding),
        ) {
            ReorderableColumn(items = books, key = { it.id }, onMove = viewModel::move) { book, index, handle ->
                ReorderRow(
                    book = book,
                    index = index,
                    handle = handle,
                    onClick = { onOpenBook(book.id) },
                    onRemove = { viewModel.remove(book.id) },
                )
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ReorderRow(
    book: LibraryBook,
    index: Int,
    handle: Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp),
        )
        BookCover(
            title = book.title,
            coverPath = book.coverPath,
            format = book.format,
            modifier = Modifier
                .width(36.dp)
                .aspectRatio(0.66f),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = book.author ?: stringResource(R.string.unknown_author),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove))
        }
        Box(handle.padding(8.dp)) {
            Icon(
                Icons.Rounded.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    viewModel: TrashViewModel = folioViewModel { TrashViewModel(it) },
) {
    val books by viewModel.books.collectAsStateWithLifecycle()
    val retention by viewModel.retentionDays.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                text = stringResource(R.string.trash_note, retention),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = spacing.screenPadding, vertical = 10.dp),
            )
            if (books.isEmpty()) {
                EmptyState(stringResource(R.string.empty_trash), modifier = Modifier.fillMaxSize())
                return@Column
            }
            LazyColumn(
                contentPadding = PaddingValues(
                    start = spacing.screenPadding,
                    end = spacing.screenPadding,
                    bottom = 80.dp,
                ),
            ) {
                items(books, key = { it.id }) { book ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                text = Format.fileSize(book.fileSize),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.restore(book.id) }) {
                            Icon(
                                Icons.Rounded.Restore,
                                contentDescription = stringResource(R.string.trash_restore),
                            )
                        }
                        IconButton(onClick = { viewModel.purge(book.id) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = stringResource(R.string.trash_delete_forever),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}
