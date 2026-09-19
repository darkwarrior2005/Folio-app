package app.folio.ui.screens.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.data.db.TrackEntity
import app.folio.ui.LocalContainer
import app.folio.ui.util.Format

@Composable
fun PlayerSheet(onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val player = container.musicPlayer
    val now by player.state.collectAsStateWithLifecycle()
    val queue by produceState(emptyList<TrackEntity>(), now.queue) { value = container.music.tracksForIds(now.queue) }
    var seeking by remember { mutableStateOf<Float?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(now.title.orEmpty(), style = MaterialTheme.typography.titleLarge, maxLines = 2)
            now.artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Slider(
                value = seeking ?: if (now.durationMs > 0) now.positionMs.toFloat() / now.durationMs else 0f,
                onValueChange = { seeking = it },
                onValueChangeFinished = {
                    seeking?.let { player.seekTo((it * now.durationMs).toLong()) }
                    seeking = null
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(Format.timer(now.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(Format.timer(now.durationMs), style = MaterialTheme.typography.labelSmall)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconToggleButton(checked = now.shuffle, onCheckedChange = player::setShuffle) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = stringResource(R.string.music_shuffle))
                }
                IconButton(onClick = player::previous) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.music_previous))
                }
                FilledIconButton(onClick = player::toggle) {
                    Icon(
                        if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (now.isPlaying) R.string.music_pause else R.string.music_play),
                    )
                }
                IconButton(onClick = player::next) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.music_next))
                }
                IconButton(onClick = {
                    player.stop()
                    onDismiss()
                }) {
                    Icon(Icons.Rounded.Stop, contentDescription = stringResource(R.string.music_stop))
                }
            }
            Text(stringResource(R.string.music_queue), style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(queue, key = { index, track -> "${track.id}-$index" }) { index, track ->
                    ListItem(
                        headlineContent = {
                            Text(
                                track.displayTitle,
                                maxLines = 1,
                                color = if (index == now.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        supportingContent = { track.displayArtist?.let { Text(it, maxLines = 1) } },
                        modifier = Modifier.clickable { player.skipTo(index) },
                    )
                }
            }
        }
    }
}
