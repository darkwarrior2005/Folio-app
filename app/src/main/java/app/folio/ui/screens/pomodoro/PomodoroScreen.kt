package app.folio.ui.screens.pomodoro

import app.folio.ui.components.rememberNotificationPermissionRequest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.data.db.PomodoroPhase
import app.folio.ui.folioViewModel
import app.folio.ui.theme.LocalSpacing
import app.folio.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    viewModel: PomodoroViewModel = folioViewModel { PomodoroViewModel(it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val remaining by viewModel.remaining.collectAsStateWithLifecycle()
    val today by viewModel.todaySessions.collectAsStateWithLifecycle()
    val focusTime by viewModel.todayFocusMs.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val askNotifications = rememberNotificationPermissionRequest()

    val fraction = if (state.plannedMs > 0 && state.active) {
        1f - (remaining.toFloat() / state.plannedMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pomodoro_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSettings) { Text(stringResource(R.string.nav_settings)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PomodoroPhase.entries.forEach { phase ->
                    FilterChip(
                        selected = state.phase == phase,
                        onClick = { askNotifications(); viewModel.start(phase) },
                        label = { Text(phase.label()) },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                val ring = MaterialTheme.colorScheme.primary
                val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = Stroke(width = 18f, cap = StrokeCap.Round)
                    val inset = stroke.width / 2
                    val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                    drawArc(
                        color = track,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke,
                    )
                    drawArc(
                        color = ring,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = stroke,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (state.active) Format.timer(remaining) else "--:--",
                        style = MaterialTheme.typography.displayLarge,
                    )
                    Text(
                        text = state.phase.label(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!state.active) {
                    Button(onClick = { askNotifications(); viewModel.start(state.phase) }) {
                        Text(stringResource(R.string.pomodoro_start))
                    }
                } else {
                    Button(onClick = { if (state.running) viewModel.pause() else viewModel.resume() }) {
                        Text(
                            stringResource(
                                if (state.running) R.string.pomodoro_pause else R.string.pomodoro_resume,
                            ),
                        )
                    }
                    OutlinedButton(onClick = viewModel::skip) { Text(stringResource(R.string.pomodoro_skip)) }
                    OutlinedButton(onClick = viewModel::stop) { Text(stringResource(R.string.pomodoro_stop)) }
                }
            }

            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = today.toString(), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = pluralStringResource(R.plurals.pomodoro_today_sessions, today, today),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = Format.shortDuration(focusTime),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.pomodoro_focus),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PomodoroPhase.label(): String = stringResource(
    when (this) {
        PomodoroPhase.FOCUS -> R.string.pomodoro_focus
        PomodoroPhase.SHORT_BREAK -> R.string.pomodoro_short_break
        PomodoroPhase.LONG_BREAK -> R.string.pomodoro_long_break
    },
)
