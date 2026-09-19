package app.folio.ui.screens.music

import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.data.db.TrackEntity
import app.folio.ui.components.EmptyState
import app.folio.ui.components.TextInputDialog
import app.folio.ui.folioViewModel
import app.folio.ui.util.Format
import coil3.compose.AsyncImage

@Composable
fun MusicLibraryScreen(
    onBack: () -> Unit,
    onEditTrack: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    viewModel: MusicViewModel = folioViewModel { MusicViewModel(it) },
) {
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showImportMenu by remember { mutableStateOf(false) }
    var showNewCollection by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<Pair<TrackEntity, TrackRemoval>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris.map { it.toString() })
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.importFolder(it.toString()) }
    }

    LaunchedEffect(viewModel) {
        viewModel.importSummary.collect { summary ->
            snackbar.showSnackbar(
                context.getString(R.string.music_import_summary, summary.added, summary.duplicates, summary.unreadable),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.music_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = { if (tab == 1) showNewCollection = true else showImportMenu = true },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(if (tab == 1) R.string.music_new_collection else R.string.music_import)) },
                )
                DropdownMenu(expanded = showImportMenu, onDismissRequest = { showImportMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_import_files)) },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                        onClick = {
                            showImportMenu = false
                            pickFiles.launch(arrayOf("audio/*"))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.music_import_folder)) },
                        leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                        onClick = {
                            showImportMenu = false
                            pickFolder.launch(null)
                        },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { NowPlayingBar(Modifier.padding(8.dp)) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.music_tab_tracks, R.string.music_tab_collections, R.string.music_tab_tags)
                    .forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                    }
            }
            when (tab) {
                0 -> TracksTab(
                    tracks = tracks,
                    query = query,
                    onQuery = viewModel::setQuery,
                    tags = tags.map { it.tag.id to it.tag.name },
                    tagFilter = tagFilter,
                    onTagFilter = viewModel::setTagFilter,
                    onPlay = { tapped ->
                        val playable = tracks.filterNot { it.missing }
                        viewModel.play(playable, playable.indexOfFirst { it.id == tapped.id }.coerceAtLeast(0))
                    },
                    onEdit = onEditTrack,
                    onRemove = { track, removal -> pendingRemoval = track to removal },
                )
                1 -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    if (collections.isEmpty()) {
                        item { EmptyState(title = stringResource(R.string.music_no_collections), icon = Icons.Rounded.LibraryMusic) }
                    }
                    items(collections, key = { it.collection.id }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.collection.name) },
                            supportingContent = {
                                Text(pluralStringResource(R.plurals.music_track_count, entry.trackCount, entry.trackCount))
                            },
                            leadingContent = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                            modifier = Modifier.clickable { onOpenCollection(entry.collection.id) },
                        )
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(tags, key = { it.tag.id }) { entry ->
                        ListItem(
                            headlineContent = { Text(entry.tag.name) },
                            supportingContent = {
                                Text(pluralStringResource(R.plurals.music_track_count, entry.trackCount, entry.trackCount))
                            },
                            modifier = Modifier.clickable {
                                viewModel.setTagFilter(entry.tag.id)
                                tab = 0
                            },
                        )
                    }
                }
            }
        }
    }

    pendingRemoval?.let { (track, removal) ->
        TrackRemovalDialog(
            track = track,
            removal = removal,
            onDismiss = { pendingRemoval = null },
            onFinished = { message, _ ->
                viewModel.viewModelScope.launch { snackbar.showSnackbar(context.getString(message)) }
            },
        )
    }

    if (showNewCollection) {
        TextInputDialog(
            title = stringResource(R.string.music_new_collection),
            label = stringResource(R.string.field_name),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = {
                viewModel.createCollection(it)
                showNewCollection = false
            },
            onDismiss = { showNewCollection = false },
        )
    }
}

@Composable
private fun TracksTab(
    tracks: List<TrackEntity>,
    query: String,
    onQuery: (String) -> Unit,
    tags: List<Pair<Long, String>>,
    tagFilter: Long?,
    onTagFilter: (Long?) -> Unit,
    onPlay: (TrackEntity) -> Unit,
    onEdit: (Long) -> Unit,
    onRemove: (TrackEntity, TrackRemoval) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.music_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (tags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(tags, key = { it.first }) { (id, name) ->
                    FilterChip(
                        selected = tagFilter == id,
                        onClick = { onTagFilter(if (tagFilter == id) null else id) },
                        label = { Text(name) },
                    )
                }
            }
        }
        if (tracks.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.music_empty_title),
                message = stringResource(R.string.music_empty_message),
                icon = Icons.Rounded.MusicNote,
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { _, track ->
                ListItem(
                    headlineContent = { Text(track.displayTitle, maxLines = 1) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                track.displayArtist,
                                Format.timer(track.durationMs),
                                stringResource(R.string.music_missing).takeIf { track.missing },
                            ).joinToString(" · "),
                            maxLines = 1,
                        )
                    },
                    leadingContent = {
                        if (track.coverPath != null) {
                            AsyncImage(
                                model = java.io.File(track.coverPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        } else {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp).padding(12.dp))
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onPlay(track) }, enabled = !track.missing) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.music_play))
                            }
                            TrackMenu(
                                onEdit = { onEdit(track.id) },
                                onRemove = { onRemove(track, TrackRemoval.LIBRARY_ONLY) },
                                onDelete = { onRemove(track, TrackRemoval.DELETE_FILE) },
                            )
                        }
                    },
                    modifier = Modifier
                        .alpha(if (track.missing) 0.5f else 1f)
                        .clickable(enabled = !track.missing) { onPlay(track) },
                )
            }
        }
    }
}

@Composable
private fun TrackMenu(onEdit: () -> Unit, onRemove: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_edit)) },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    open = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_remove_from_library)) },
                leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = null) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.music_delete_file), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onDelete()
                },
            )
        }
    }
}
