package app.folio.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.BookMusicMode
import app.folio.core.model.MusicSwitchPolicy
import app.folio.data.db.BookMusicSourceEntity
import app.folio.data.db.TrackEntity
import app.folio.data.repo.BookMusicSettings
import app.folio.data.repo.CollectionMusicSettings
import app.folio.ui.LocalContainer
import app.folio.ui.components.BookCover
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/** What reading music is being set for: one book, or a whole collection (a shelf). */
private sealed interface MusicTarget {
    val id: Long
    val name: String

    data class Book(override val id: Long, override val name: String) : MusicTarget
    data class Shelf(override val id: Long, override val name: String) : MusicTarget
}

/**
 * The home player's "Reading music" sheet: pick a book or a collection, then the playlists and
 * songs it plays when opened. A collection's music is played by its books that have none of
 * their own. New playlists can be made right here from the songs you tick.
 */
@Composable
fun MusicAssignSheet(onDismiss: () -> Unit, onOpenMusicLibrary: () -> Unit) {
    var target by remember { mutableStateOf<MusicTarget?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        val chosen = target
        if (chosen == null) {
            TargetPicker(onPick = { target = it })
        } else {
            MusicPicker(
                target = chosen,
                onBack = { target = null },
                onDone = onDismiss,
                onOpenMusicLibrary = onOpenMusicLibrary,
            )
        }
    }
}

@Composable
private fun TargetPicker(onPick: (MusicTarget) -> Unit) {
    val container = LocalContainer.current
    val books by container.library.libraryBooks.collectAsStateWithLifecycle(emptyList())
    val collections by container.organization.collections.collectAsStateWithLifecycle(emptyList())
    val booksWithMusic by container.music.booksWithMusic.collectAsStateWithLifecycle(emptyList())
    val shelvesWithMusic by container.music.collectionsWithMusic.collectAsStateWithLifecycle(emptyList())
    var showShelves by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().lowercase(Locale.ROOT)

    // Reading music plays for PDF and EPUB books; list what you're reading first.
    val bookRows = remember(books, needle) {
        books
            .filter { MusicSwitchPolicy.supports(it.format.family) }
            .filter {
                needle.isEmpty() ||
                    it.title.lowercase(Locale.ROOT).contains(needle) ||
                    it.author?.lowercase(Locale.ROOT)?.contains(needle) == true
            }
            .sortedWith(compareByDescending<app.folio.core.model.LibraryBook> { it.lastOpenedAt ?: 0L }.thenBy { it.sortTitle })
    }
    val shelfRows = remember(collections, needle) {
        collections.filter { needle.isEmpty() || it.collection.name.lowercase(Locale.ROOT).contains(needle) }
    }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(stringResource(R.string.music_assign_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.music_assign_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(top = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !showShelves,
                onClick = { showShelves = false },
                label = { Text(stringResource(R.string.music_assign_books)) },
            )
            FilterChip(
                selected = showShelves,
                onClick = { showShelves = true },
                label = { Text(stringResource(R.string.music_assign_collections)) },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.action_search)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
    ) {
        if (!showShelves) {
            if (bookRows.isEmpty()) {
                item { EmptyLine(stringResource(R.string.music_assign_no_books)) }
            }
            items(bookRows, key = { it.id }) { book ->
                val hasMusic = book.id in booksWithMusic
                ListItem(
                    headlineContent = { Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            text = if (hasMusic) {
                                stringResource(R.string.music_assign_has_music)
                            } else {
                                book.author ?: book.format.label
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingContent = {
                        BookCover(
                            title = book.title,
                            coverPath = book.coverPath,
                            format = book.format,
                            modifier = Modifier
                                .width(34.dp)
                                .aspectRatio(0.68f),
                            shape = RoundedCornerShape(4.dp),
                        )
                    },
                    trailingContent = { if (hasMusic) MusicMark() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable { onPick(MusicTarget.Book(book.id, book.title)) },
                )
            }
            item { EmptyLine(stringResource(R.string.music_assign_formats_note)) }
        } else {
            if (shelfRows.isEmpty()) {
                item { EmptyLine(stringResource(R.string.music_assign_no_collections)) }
            }
            items(shelfRows, key = { it.collection.id }) { entry ->
                val hasMusic = entry.collection.id in shelvesWithMusic
                ListItem(
                    headlineContent = { Text(entry.collection.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            if (hasMusic) {
                                stringResource(R.string.music_assign_has_music)
                            } else {
                                pluralStringResource(R.plurals.library_books_count, entry.bookCount, entry.bookCount)
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null) },
                    trailingContent = { if (hasMusic) MusicMark() },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.clickable {
                        onPick(MusicTarget.Shelf(entry.collection.id, entry.collection.name))
                    },
                )
            }
            item { EmptyLine(stringResource(R.string.music_assign_collection_note)) }
        }
    }
}

@Composable
private fun MusicPicker(
    target: MusicTarget,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenMusicLibrary: () -> Unit,
) {
    val container = LocalContainer.current
    val music = container.music
    val scope = rememberCoroutineScope()
    val playlists by music.collections.collectAsStateWithLifecycle(emptyList())
    val tracks by music.tracks.collectAsStateWithLifecycle(emptyList())
    val now by container.musicPlayer.state.collectAsStateWithLifecycle()

    val playlistIds = remember(target) { mutableStateListOf<Long>() }
    val trackIds = remember(target) { mutableStateListOf<Long>() }
    var selection by remember(target) { mutableStateOf(emptyList<Long>()) }
    var mode by remember(target) { mutableStateOf(BookMusicMode.LOOP) }
    var autoplay by remember(target) { mutableStateOf(true) }
    var hadMusic by remember(target) { mutableStateOf(false) }
    var songQuery by rememberSaveable { mutableStateOf("") }
    var creatingPlaylist by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        when (target) {
            is MusicTarget.Book -> music.observeBookMusic(target.id).first()?.let { saved ->
                hadMusic = saved.sources.isNotEmpty()
                mode = saved.mode
                autoplay = saved.autoplay
                selection = saved.selection
                playlistIds += saved.sources.mapNotNull { it.collectionId }
                trackIds += saved.sources.mapNotNull { it.trackId }
            }
            is MusicTarget.Shelf -> music.observeCollectionMusic(target.id).first()?.let { saved ->
                hadMusic = saved.playlistIds.isNotEmpty() || saved.trackIds.isNotEmpty()
                mode = saved.mode
                autoplay = saved.autoplay
                playlistIds += saved.playlistIds
                trackIds += saved.trackIds
            }
        }
    }

    fun save(playAfter: Boolean) {
        scope.launch {
            val empty = playlistIds.isEmpty() && trackIds.isEmpty()
            when (target) {
                is MusicTarget.Book -> if (empty) {
                    music.clearBookMusic(target.id)
                } else {
                    val sources = playlistIds.map { BookMusicSourceEntity(bookId = target.id, collectionId = it, position = 0) } +
                        trackIds.map { BookMusicSourceEntity(bookId = target.id, trackId = it, position = 0) }
                    music.saveBookMusic(target.id, BookMusicSettings(mode, autoplay, sources, selection))
                    if (playAfter) container.bookMusic.playBook(target.id)
                }
                is MusicTarget.Shelf -> if (empty) {
                    music.clearCollectionMusic(target.id)
                } else {
                    music.saveCollectionMusic(
                        target.id,
                        CollectionMusicSettings(mode, autoplay, playlistIds.toList(), trackIds.toList()),
                    )
                }
            }
            onDone()
        }
    }

    val needle = songQuery.trim().lowercase(Locale.ROOT)
    val songRows = remember(tracks, needle) {
        tracks.filter {
            needle.isEmpty() ||
                it.displayTitle.lowercase(Locale.ROOT).contains(needle) ||
                it.displayArtist?.lowercase(Locale.ROOT)?.contains(needle) == true
        }
    }

    Row(
        Modifier.padding(start = 8.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (target is MusicTarget.Book) R.string.music_assign_for_book else R.string.music_assign_for_collection,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = target.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (tracks.isEmpty()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.music_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.music_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 12.dp))
            Button(onClick = { onDone(); onOpenMusicLibrary() }) { Text(stringResource(R.string.home_music_library)) }
        }
        Spacer(Modifier.padding(bottom = 24.dp))
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp),
    ) {
        val playingId = now.trackId
        if (playingId != null && playingId !in trackIds) {
            item(key = "now-playing") {
                AssistChip(
                    onClick = { trackIds += playingId },
                    label = {
                        Text(
                            stringResource(R.string.music_assign_use_playing, now.title.orEmpty()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }

        item(key = "playlists-header") {
            SheetSectionHeader(
                title = stringResource(R.string.music_assign_playlists),
                action = stringResource(R.string.music_assign_new_playlist),
                onAction = { creatingPlaylist = true },
            )
        }
        if (playlists.isEmpty()) {
            item(key = "no-playlists") { EmptyLine(stringResource(R.string.music_assign_no_playlists)) }
        }
        items(playlists, key = { "p" + it.collection.id }) { entry ->
            CheckRow(
                checked = entry.collection.id in playlistIds,
                onToggle = { playlistIds.toggle(entry.collection.id) },
                title = entry.collection.name,
                subtitle = pluralStringResource(R.plurals.music_track_count, entry.trackCount, entry.trackCount),
                icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
            )
        }

        item(key = "songs-header") {
            SheetSectionHeader(title = stringResource(R.string.music_tab_tracks))
            OutlinedTextField(
                value = songQuery,
                onValueChange = { songQuery = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.music_search)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        items(songRows, key = { "t" + it.id }) { track ->
            CheckRow(
                checked = track.id in trackIds,
                onToggle = { trackIds.toggle(track.id) },
                title = track.displayTitle,
                subtitle = track.displayArtist,
                icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                enabled = !track.missing,
            )
        }
    }

    HorizontalDivider()
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        if (mode == BookMusicMode.SELECTION) {
            Text(
                text = stringResource(R.string.music_assign_selection_kept),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SwitchLine(
            label = stringResource(R.string.music_mode_shuffle),
            checked = mode == BookMusicMode.SHUFFLE,
            onChange = { mode = if (it) BookMusicMode.SHUFFLE else BookMusicMode.LOOP },
        )
        SwitchLine(
            label = stringResource(R.string.music_assign_autoplay),
            checked = autoplay,
            onChange = { autoplay = it },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hadMusic) {
                TextButton(onClick = {
                    playlistIds.clear()
                    trackIds.clear()
                    save(playAfter = false)
                }) { Text(stringResource(R.string.music_remove_all)) }
            }
            Spacer(Modifier.weight(1f))
            val chosen = playlistIds.isNotEmpty() || trackIds.isNotEmpty()
            if (target is MusicTarget.Book) {
                OutlinedButton(onClick = { save(playAfter = true) }, enabled = chosen) {
                    Text(stringResource(R.string.music_assign_save_play))
                }
            }
            Button(onClick = { save(playAfter = false) }, enabled = chosen || hadMusic) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (creatingPlaylist) {
        NewPlaylistDialog(
            tracks = tracks.filterNot { it.missing },
            preselected = trackIds.toList(),
            onDismiss = { creatingPlaylist = false },
            onCreate = { name, ids ->
                creatingPlaylist = false
                scope.launch {
                    val id = music.createPlaylist(name, ids)
                    // The new playlist replaces the loose songs it was made from.
                    playlistIds += id
                    trackIds.removeAll(ids)
                }
            },
        )
    }
}

/** Name a playlist and pick its songs; the songs already ticked in the sheet start ticked. */
@Composable
private fun NewPlaylistDialog(
    tracks: List<TrackEntity>,
    preselected: List<Long>,
    onDismiss: () -> Unit,
    onCreate: (String, List<Long>) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>().apply { addAll(preselected) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.music_assign_new_playlist)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.music_assign_playlist_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.padding(top = 8.dp))
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(tracks, key = { it.id }) { track ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picked.toggle(track.id) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = track.id in picked, onCheckedChange = { picked.toggle(track.id) })
                            Text(track.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), picked.toList()) },
                enabled = name.isNotBlank() && picked.isNotEmpty(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun SheetSectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            TextButton(onClick = onAction) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(action)
            }
        }
    }
}

@Composable
private fun CheckRow(
    checked: Boolean,
    onToggle: () -> Unit,
    title: String,
    subtitle: String?,
    icon: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        leadingContent = icon,
        trailingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable(enabled = enabled, onClick = onToggle),
    )
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MusicMark() {
    Icon(
        Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

private fun SnapshotStateList<Long>.toggle(id: Long) {
    if (id in this) remove(id) else add(id)
}
