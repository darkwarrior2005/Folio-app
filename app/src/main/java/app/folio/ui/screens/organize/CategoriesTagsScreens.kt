package app.folio.ui.screens.organize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.EmptyState
import app.folio.ui.components.PickerDialogHost
import app.folio.ui.components.TextInputDialog
import app.folio.ui.folioViewModel
import app.folio.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CollectionsViewModel = folioViewModel { CollectionsViewModel(it) },
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
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
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_create))
            }
        },
    ) { padding ->
        if (categories.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.categories_title),
                message = stringResource(R.string.empty_collections_message),
                actionLabel = stringResource(R.string.action_create),
                onAction = { creating = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(
                    start = spacing.screenPadding,
                    end = spacing.screenPadding,
                    bottom = 96.dp,
                ),
            ) {
                items(categories, key = { it.category.id }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.category.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = pluralStringResource(R.plurals.library_books_count, entry.bookCount, entry.bookCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { renaming = entry.category.id to entry.category.name }) {
                            Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_rename))
                        }
                        IconButton(onClick = { deleting = entry.category.id }) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = stringResource(R.string.dialog_new_category),
            label = stringResource(R.string.dialog_name),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = {
                viewModel.createCategory(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }

    renaming?.let { (id, name) ->
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.dialog_name),
            initialValue = name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                viewModel.renameCategory(id, it)
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
                viewModel.deleteCategory(id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onBack: () -> Unit,
    onOpenTag: (Long) -> Unit,
    viewModel: CollectionsViewModel = folioViewModel { CollectionsViewModel(it) },
) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    var renaming by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var deleting by remember { mutableStateOf<Long?>(null) }
    var merging by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags_title)) },
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
        if (tags.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.tags_title),
                message = stringResource(R.string.tag_hint),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = spacing.screenPadding,
                end = spacing.screenPadding,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(tags, key = { it.tag.id }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { onOpenTag(entry.tag.id) },
                    ) {
                        Text(entry.tag.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = pluralStringResource(R.plurals.library_books_count, entry.bookCount, entry.bookCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { merging = entry.tag.id }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.MergeType,
                            contentDescription = stringResource(R.string.action_more),
                        )
                    }
                    IconButton(onClick = { renaming = entry.tag.id to entry.tag.name }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_rename))
                    }
                    IconButton(onClick = { deleting = entry.tag.id }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            }
        }
    }

    renaming?.let { (id, name) ->
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.dialog_name),
            initialValue = name,
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                viewModel.renameTag(id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.action_delete),
            message = stringResource(R.string.dialog_delete_tag_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteTag(id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }

    merging?.let { fromId ->
        PickerDialogHost(
            title = stringResource(R.string.action_more),
            options = tags.filter { it.tag.id != fromId }.map { it.tag.id to it.tag.name },
            onPick = { intoId ->
                viewModel.mergeTags(fromId, intoId)
                merging = null
            },
            onDismiss = { merging = null },
        )
    }
}
