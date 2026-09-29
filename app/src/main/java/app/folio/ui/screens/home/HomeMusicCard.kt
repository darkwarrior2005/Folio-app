package app.folio.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.ui.LocalContainer
import app.folio.ui.components.ProgressBar
import app.folio.ui.screens.music.PlayerSheet
import app.folio.ui.theme.LocalReduceMotion
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.io.File

/**
 * The home screen's music player. It drives the app's one music player: tapping it opens the full
 * player sheet, the add-to-playlist button opens "Reading music" (music for a book or a whole
 * collection, and new playlists), and the list button opens the music library. With nothing
 * queued it becomes a slim card that opens "Reading music".
 */
@Composable
fun HomeMusicCard(
    onOpenMusicLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = LocalContainer.current.musicPlayer
    val now by player.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf(false) }
    var assigning by rememberSaveable { mutableStateOf(false) }
    val reduceMotion = LocalReduceMotion.current

    if (assigning) {
        MusicAssignSheet(onDismiss = { assigning = false }, onOpenMusicLibrary = onOpenMusicLibrary)
    }

    if (!now.hasQueue) {
        IdleMusicCard(
            onAssign = { assigning = true },
            onOpenMusicLibrary = onOpenMusicLibrary,
            modifier = modifier,
        )
        return
    }

    Surface(
        onClick = { sheet = true },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicArtwork(now.coverPath, size = 76.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(top = 2.dp)) {
                        Text(
                            text = now.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = now.artist ?: stringResource(R.string.home_music_unknown_artist),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { assigning = true }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = stringResource(R.string.music_assign_open),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onOpenMusicLibrary) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = stringResource(R.string.home_music_library),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = player::previous) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.music_previous))
                    }
                    FilledTonalIconButton(onClick = player::toggle) {
                        Crossfade(
                            targetState = now.isPlaying,
                            animationSpec = if (reduceMotion) snap() else tween(160),
                            label = "play-pause",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(
                                    if (playing) R.string.music_pause else R.string.music_play,
                                ),
                            )
                        }
                    }
                    IconButton(onClick = player::next) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.music_next))
                    }
                }
                val progress = if (now.durationMs > 0) now.positionMs.toFloat() / now.durationMs else 0f
                ProgressBar(progress, Modifier.fillMaxWidth().padding(end = 6.dp), height = 3.dp)
            }
        }
    }

    if (sheet) PlayerSheet(onDismiss = { sheet = false })
}

/** Nothing playing: tap to set reading music; the list button opens the music library. */
@Composable
private fun IdleMusicCard(onAssign: () -> Unit, onOpenMusicLibrary: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onAssign,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MusicArtwork(coverPath = null, size = 44.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_music_idle_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_music_idle_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenMusicLibrary) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.home_music_library),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A track's cover, or a quiet placeholder in the accent colour. */
@Composable
private fun MusicArtwork(coverPath: String?, size: Dp) {
    val exists = remember(coverPath) { coverPath != null && File(coverPath).exists() }
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.55f))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (exists) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(coverPath!!)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}
