package app.folio.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.folio.core.model.PlaybackSource
import app.folio.data.db.TrackEntity
import app.folio.data.repo.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class NowPlaying(
    val source: PlaybackSource?,
    val trackId: Long?,
    val title: String?,
    val artist: String?,
    val coverPath: String?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val queue: List<Long>,
    val index: Int,
    val shuffle: Boolean,
    val repeatAll: Boolean,
) {
    val hasQueue: Boolean get() = queue.isNotEmpty()

    companion object {
        val Idle = NowPlaying(null, null, null, null, null, false, 0, 0, emptyList(), -1, false, true)
    }
}

/**
 * The app's handle on the playback service. Commands issued before the controller connects are
 * queued, so callers never wait for the service.
 */
class MusicPlayer(
    private val context: Context,
    private val music: MusicRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NowPlaying.Idle)
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val _skippedMissing = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val skippedMissing: SharedFlow<String> = _skippedMissing.asSharedFlow()

    private var controller: MediaController? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private var connecting = false
    private var source: PlaybackSource? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Runs [action] on the main thread with a connected controller. MediaController may only be used
     * from the thread it was built on, and callers such as the book coordinator run in the
     * background, so every command is posted to the main thread here.
     */
    private fun withController(action: (MediaController) -> Unit) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { withController(action) }
            return
        }
        controller?.let { action(it); return }
        pending += action
        if (connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull()
                connecting = false
                if (connected == null) {
                    pending.clear()
                    return@addListener
                }
                controller = connected
                connected.addListener(listener)
                while (pending.isNotEmpty()) pending.removeFirst().invoke(connected)
                publish()
                startProgressTicker()
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) {
            val player = controller ?: return
            val title = player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            player.currentMediaItem?.mediaId?.toLongOrNull()?.let { id ->
                scope.launch(Dispatchers.IO) { music.markMissing(id) }
            }
            _skippedMissing.tryEmit(title)
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }
    }

    fun playTracks(
        tracks: List<TrackEntity>,
        source: PlaybackSource,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        fade: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        withController { player ->
            scope.launch(Dispatchers.Main) {
                if (fade && player.isPlaying) fadeTo(player, 0f)
                this@MusicPlayer.source = source
                player.setMediaItems(tracks.map { it.toMediaItem() }, startIndex.coerceIn(0, tracks.lastIndex), 0)
                player.shuffleModeEnabled = shuffle
                player.repeatMode = Player.REPEAT_MODE_ALL
                player.volume = if (fade) 0f else 1f
                player.prepare()
                player.play()
                if (fade) fadeTo(player, 1f)
                publish()
            }
        }
    }

    fun play() = withController { it.play() }
    fun pause() = withController { it.pause() }
    fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs) }
    fun skipTo(index: Int) = withController { it.seekToDefaultPosition(index) }
    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }

    /** Drops removed tracks from the queue; if nothing is left, playback stops. */
    fun removeFromQueue(trackIds: Collection<Long>) = withController { player ->
        val ids = trackIds.map(Long::toString).toSet()
        for (index in player.mediaItemCount - 1 downTo 0) {
            if (player.getMediaItemAt(index).mediaId in ids) player.removeMediaItem(index)
        }
        if (player.mediaItemCount == 0) source = null
        publish()
    }

    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
        source = null
        publish()
    }

    private suspend fun fadeTo(player: Player, target: Float) {
        val start = player.volume
        val steps = FADE_STEPS
        for (step in 1..steps) {
            player.volume = start + (target - start) * step / steps
            delay(FADE_MS / steps)
        }
    }

    private fun startProgressTicker() {
        scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (controller?.isPlaying == true) publish()
                delay(500)
            }
        }
    }

    private fun publish() {
        val player = controller ?: return
        val item = player.currentMediaItem
        _state.value = NowPlaying(
            source = source.takeIf { player.mediaItemCount > 0 },
            trackId = item?.mediaId?.toLongOrNull(),
            title = item?.mediaMetadata?.title?.toString(),
            artist = item?.mediaMetadata?.artist?.toString(),
            coverPath = item?.mediaMetadata?.extras?.getString(EXTRA_COVER),
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0,
            queue = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() },
            index = player.currentMediaItemIndex,
            shuffle = player.shuffleModeEnabled,
            repeatAll = player.repeatMode == Player.REPEAT_MODE_ALL,
        )
    }

    private fun TrackEntity.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(Uri.parse(uri))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(displayTitle)
                .setArtist(displayArtist)
                .setAlbumTitle(overrides.album ?: imported.album)
                .setArtworkUri(coverPath?.let { Uri.fromFile(File(it)) })
                .setExtras(android.os.Bundle().apply { putString(EXTRA_COVER, coverPath) })
                .build(),
        )
        .build()

    private companion object {
        const val FADE_MS = 400L
        const val FADE_STEPS = 10
        const val EXTRA_COVER = "folio.cover"
    }
}
