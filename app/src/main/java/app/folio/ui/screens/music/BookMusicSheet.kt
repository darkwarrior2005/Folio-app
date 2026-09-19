package app.folio.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.BookMusicMode
import app.folio.data.db.BookMusicSourceEntity
import app.folio.data.repo.BookMusicSettings
import app.folio.ui.LocalContainer
import app.folio.ui.components.SectionHeader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** "Reading music" on Book details; only shown for PDF and EPUB books. */
@Composable
fun BookMusicSection(bookId: Long) {
    val container = LocalContainer.current
    val settings by container.music.observeBookMusic(bookId).collectAsStateWithLifecycle(null)
    var editing by remember { mutableStateOf(false) }
    val count = settings?.sources?.size ?: 0

    Column {
        SectionHeader(stringResource(R.string.music_reading_music))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { editing = true },
                label = {
                    Text(
                        if (count == 0) {
                            stringResource(R.string.music_add_reading_music)
                        } else {
                            pluralStringResource(R.plurals.music_sources_count, count, count)
                        },
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
            )
            if (count > 0) {
                AssistChip(
                    onClick = { container.bookMusic.playBook(bookId) },
                    label = { Text(stringResource(R.string.music_play_book_music)) },
                    leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                )
            }
        }
    }
    if (editing) BookMusicSheet(bookId, onDismiss = { editing = false })
}

@Composable
fun BookMusicSheet(bookId: Long, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val music = container.music
    val scope = rememberCoroutineScope()
    val collections by music.collections.collectAsStateWithLifecycle(emptyList())
    val tracks by music.tracks.collectAsStateWithLifecycle(emptyList())

    var mode by remember { mutableStateOf(BookMusicMode.LOOP) }
    var autoplay by remember { mutableStateOf(true) }
    val collectionIds = remember { mutableStateListOf<Long>() }
    val trackIds = remember { mutableStateListOf<Long>() }
    val selection = remember { mutableStateListOf<Long>() }

    LaunchedEffect(bookId) {
        music.observeBookMusic(bookId).first()?.let { saved ->
            mode = saved.mode
            autoplay = saved.autoplay
            collectionIds += saved.sources.mapNotNull { it.collectionId }
            trackIds += saved.sources.mapNotNull { it.trackId }
            selection += saved.selection
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.music_reading_music))
            Spacer(Modifier.height(12.dp))

            SectionHeader(stringResource(R.string.music_tab_collections))
            collections.forEach { entry ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = entry.collection.id in collectionIds,
                        onCheckedChange = { checked ->
                            if (checked) collectionIds += entry.collection.id else collectionIds -= entry.collection.id
                        },
                    )
                    Icon(Icons.Rounded.LibraryMusic, contentDescription = null)
                    Text(entry.collection.name, modifier = Modifier.padding(start = 8.dp))
                }
            }

            SectionHeader(stringResource(R.string.music_tab_tracks))
            tracks.forEach { track ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = track.id in trackIds,
                        onCheckedChange = { checked -> if (checked) trackIds += track.id else trackIds -= track.id },
                    )
                    Text(track.displayTitle, maxLines = 1)
                }
            }

            SectionHeader(stringResource(R.string.music_mode))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookMusicMode.entries.forEach { option ->
                    FilterChip(
                        selected = mode == option,
                        onClick = { mode = option },
                        label = {
                            Text(
                                stringResource(
                                    when (option) {
                                        BookMusicMode.LOOP -> R.string.music_mode_loop
                                        BookMusicMode.SHUFFLE -> R.string.music_mode_shuffle
                                        BookMusicMode.SELECTION -> R.string.music_mode_selection
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            if (mode == BookMusicMode.SELECTION) {
                Text(stringResource(R.string.music_selection_hint))
                selectionCandidates(tracks, collectionIds, trackIds, container).forEach { track ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = track.id in selection,
                            onCheckedChange = { checked -> if (checked) selection += track.id else selection -= track.id },
                        )
                        Text(track.displayTitle, maxLines = 1)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.music_autoplay), modifier = Modifier.weight(1f))
                Switch(checked = autoplay, onCheckedChange = { autoplay = it })
            }

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    scope.launch {
                        music.clearBookMusic(bookId)
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.music_remove_all)) }
                TextButton(onClick = {
                    scope.launch {
                        val sources = collectionIds.map { BookMusicSourceEntity(bookId = bookId, collectionId = it, position = 0) } +
                            trackIds.map { BookMusicSourceEntity(bookId = bookId, trackId = it, position = 0) }
                        if (sources.isEmpty()) {
                            music.clearBookMusic(bookId)
                        } else {
                            music.saveBookMusic(bookId, BookMusicSettings(mode, autoplay, sources, selection.toList()))
                        }
                        onDismiss()
                    }
                }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun selectionCandidates(
    tracks: List<app.folio.data.db.TrackEntity>,
    collectionIds: List<Long>,
    trackIds: List<Long>,
    container: app.folio.AppContainer,
): List<app.folio.data.db.TrackEntity> {
    val links by androidx.compose.runtime.produceState(emptyMap<Long, List<Long>>(), collectionIds.toList()) {
        value = container.database.music().allCollectionLinks().groupBy({ it.collectionId }, { it.trackId })
    }
    val attached = app.folio.core.model.MusicQueueBuilder.attachedTracks(
        collectionIds.map { app.folio.core.model.MusicSourceRef(it, null) } +
            trackIds.map { app.folio.core.model.MusicSourceRef(null, it) },
        links,
        tracks.filterNot { it.missing }.map { it.id }.toSet(),
    )
    val byId = tracks.associateBy { it.id }
    return attached.mapNotNull { byId[it] }
}
