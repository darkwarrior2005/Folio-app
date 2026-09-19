package app.folio.ui.screens.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.PlaybackSource
import app.folio.ui.LocalContainer
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.ReorderableColumn
import app.folio.ui.components.TextInputDialog
import kotlinx.coroutines.launch

@Composable
fun MusicCollectionScreen(collectionId: Long, onBack: () -> Unit) {
    val container = LocalContainer.current
    val music = container.music
    val scope = rememberCoroutineScope()
    val collection by music.observeCollection(collectionId).collectAsStateWithLifecycle(null)
    val tracks by music.observeCollectionTracks(collectionId).collectAsStateWithLifecycle(emptyList())
    val library by music.tracks.collectAsStateWithLifecycle(emptyList())
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(collection?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { container.musicPlayer.playTracks(tracks.filterNot { it.missing }, PlaybackSource.Library) }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.music_play))
                    }
                    IconButton(onClick = { container.musicPlayer.playTracks(tracks.filterNot { it.missing }, PlaybackSource.Library, shuffle = true) }) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.music_shuffle))
                    }
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.action_rename))
                    }
                    IconButton(onClick = { deleting = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.music_add_tracks)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            ReorderableColumn(
                items = tracks,
                key = { it.id },
                onMove = { from, to ->
                    val ids = tracks.map { it.id }.toMutableList()
                    ids.add(to, ids.removeAt(from))
                    scope.launch { music.reorderCollection(collectionId, ids) }
                },
            ) { track, _, dragHandle ->
                ListItem(
                    headlineContent = { Text(track.displayTitle, maxLines = 1) },
                    supportingContent = { track.displayArtist?.let { Text(it, maxLines = 1) } },
                    leadingContent = { Icon(Icons.Rounded.DragHandle, contentDescription = null, modifier = dragHandle) },
                    trailingContent = {
                        IconButton(onClick = { scope.launch { music.removeFromCollection(collectionId, listOf(track.id)) } }) {
                            Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = stringResource(R.string.action_remove))
                        }
                    },
                )
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.action_rename),
            label = stringResource(R.string.field_name),
            initialValue = collection?.name.orEmpty(),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { name ->
                scope.launch { music.renameCollection(collectionId, name) }
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }

    if (deleting) {
        ConfirmDialog(
            title = stringResource(R.string.music_delete_collection),
            message = stringResource(R.string.music_delete_collection_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                scope.launch {
                    music.deleteCollection(collectionId)
                    onBack()
                }
                deleting = false
            },
            onDismiss = { deleting = false },
        )
    }

    if (adding) {
        val inCollection = tracks.map { it.id }.toSet()
        val chosen = remember { mutableStateListOf<Long>() }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(stringResource(R.string.music_add_tracks)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    library.filterNot { it.id in inCollection }.forEach { track ->
                        Row {
                            Checkbox(
                                checked = track.id in chosen,
                                onCheckedChange = { checked -> if (checked) chosen += track.id else chosen -= track.id },
                            )
                            Text(track.displayTitle, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { music.addToCollection(collectionId, chosen.toList()) }
                    adding = false
                }) { Text(stringResource(R.string.action_add)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
