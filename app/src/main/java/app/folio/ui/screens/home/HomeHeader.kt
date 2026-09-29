package app.folio.ui.screens.home

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.data.db.PomodoroPhase
import app.folio.pomodoro.PomodoroState
import app.folio.ui.util.Format
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The bookshelf home's header: the Folio mark, a live clock that opens the focus timer, and
 * shortcuts to search, settings and the collections hub. Narrow phones put the clock on its own
 * line so nothing has to shrink.
 */
@Composable
fun HomeHeader(
    pomodoro: PomodoroState,
    remainingMs: Long,
    onClock: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onLibraryHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val oneRow = maxWidth >= 560.dp
        if (oneRow) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FolioMark(Modifier.weight(1f))
                HeaderClock(pomodoro, remainingMs, onClock)
                HeaderActions(onSearch, onSettings, onLibraryHub, Modifier.weight(1f))
            }
        } else {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FolioMark(Modifier.weight(1f))
                    HeaderActions(onSearch, onSettings, onLibraryHub)
                }
                HeaderClock(pomodoro, remainingMs, onClock)
            }
        }
    }
}

@Composable
private fun FolioMark(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Two leaning volumes: a small, quiet mark rather than a logo that competes with the books.
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            val corner = CornerRadius(w * 0.06f)
            drawRoundRect(
                color = muted.copy(alpha = 0.45f),
                topLeft = Offset(w * 0.38f, h * 0.12f),
                size = Size(w * 0.22f, h * 0.76f),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(w * 0.08f, h * 0.04f),
                size = Size(w * 0.24f, h * 0.84f),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(w * 0.08f, h * 0.7f),
                size = Size(w * 0.84f, h * 0.18f),
                cornerRadius = corner,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HeaderActions(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onLibraryHub: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    Row(modifier, horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search), tint = tint)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.nav_settings), tint = tint)
        }
        IconButton(onClick = onLibraryHub) {
            Icon(
                Icons.Rounded.CollectionsBookmark,
                contentDescription = stringResource(R.string.home_your_library),
                tint = tint,
            )
        }
    }
}

/** The current time, updated on the minute. Tapping it opens the focus timer. */
@Composable
private fun HeaderClock(
    pomodoro: PomodoroState,
    remainingMs: Long,
    onOpenTimer: () -> Unit,
) {
    val context = LocalContext.current
    val now by produceState(LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val use24h = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    val time = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm", locale))
    val amPm = if (use24h) null else now.format(DateTimeFormatter.ofPattern("a", locale))

    val status = when {
        !pomodoro.active -> stringResource(R.string.home_clock_timer)
        !pomodoro.running -> stringResource(R.string.home_clock_paused, Format.timer(remainingMs))
        pomodoro.phase == PomodoroPhase.FOCUS -> stringResource(R.string.home_clock_focus, Format.timer(remainingMs))
        else -> stringResource(R.string.home_clock_break, Format.timer(remainingMs))
    }
    val spoken = stringResource(R.string.home_clock_description, listOfNotNull(time, amPm).joinToString(" "), status)

    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpenTimer)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = null) { onOpenTimer(); true }
            }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (amPm != null) {
            Text(
                text = amPm,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            shape = CircleShape,
            color = if (pomodoro.active) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = if (pomodoro.active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}
