package app.folio.ui.screens.music

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.folio.R
import app.folio.data.db.TrackEntity
import app.folio.ui.LocalContainer
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The two ways a track can go, kept apart like books: forgetting it, or deleting its file. */
enum class TrackRemoval { LIBRARY_ONLY, DELETE_FILE }

/**
 * Confirms and performs a removal. [onFinished] receives the message to show: removed, deleted, or
 * why the file could not be deleted (in which case nothing was removed).
 */
@Composable
fun TrackRemovalDialog(
    track: TrackEntity,
    removal: TrackRemoval,
    onDismiss: () -> Unit,
    onFinished: (message: Int, removed: Boolean) -> Unit,
) {
    val container = LocalContainer.current
    // The dialog closes before the work finishes, which would cancel a composition scope,
    // so the removal runs in the app scope and reports back on the main thread.
    val scope = container.appScope

    fun finish(message: Int, removed: Boolean) {
        if (removed) container.musicPlayer.removeFromQueue(listOf(track.id))
        onFinished(message, removed)
    }

    when (removal) {
        TrackRemoval.LIBRARY_ONLY -> ConfirmDialog(
            title = stringResource(R.string.music_remove_confirm_title),
            message = stringResource(R.string.music_remove_confirm_message, track.displayTitle),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                onDismiss()
                scope.launch {
                    container.music.removeFromLibrary(listOf(track.id))
                    withContext(Dispatchers.Main) { finish(R.string.music_removed, removed = true) }
                }
            },
            onDismiss = onDismiss,
        )

        TrackRemoval.DELETE_FILE -> ConfirmDialog(
            title = stringResource(R.string.music_delete_confirm_title),
            message = stringResource(
                R.string.music_delete_confirm_message,
                track.displayTitle,
                track.fileName,
                Format.fileSize(track.fileSize),
            ),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                onDismiss()
                scope.launch {
                    val deleted = container.music.deleteFileAndForget(track.id)
                    withContext(Dispatchers.Main) {
                        if (deleted) {
                            finish(R.string.music_deleted, removed = true)
                        } else {
                            finish(R.string.music_delete_failed, removed = false)
                        }
                    }
                }
            },
            onDismiss = onDismiss,
        )
    }
}
