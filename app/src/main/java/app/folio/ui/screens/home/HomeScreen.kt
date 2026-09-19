package app.folio.ui.screens.home

import app.folio.ui.components.rememberNotificationPermissionRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.LibraryBook
import app.folio.data.settings.HomeSection
import app.folio.ui.components.BookCover
import app.folio.ui.components.EmptyState
import app.folio.ui.components.ProgressBar
import app.folio.ui.components.SectionHeader
import app.folio.ui.folioViewModel
import app.folio.ui.theme.LocalSpacing
import app.folio.ui.util.Format
import java.time.LocalTime

@Composable
fun HomeScreen(
    onOpenBook: (Long) -> Unit,
    onBookDetails: (Long) -> Unit,
    onSeeLibrary: () -> Unit,
    onCollections: () -> Unit,
    onCollection: (Long) -> Unit,
    onStats: () -> Unit,
    onQueue: () -> Unit,
    onPomodoro: () -> Unit,
    viewModel: HomeViewModel = folioViewModel { HomeViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val continueReading by viewModel.continueReading.collectAsStateWithLifecycle()
    val recentlyAdded by viewModel.recentlyAdded.collectAsStateWithLifecycle()
    val collections by viewModel.collections.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val pomodoro by viewModel.pomodoro.collectAsStateWithLifecycle()
    val remaining by viewModel.pomodoroRemaining.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val askNotifications = rememberNotificationPermissionRequest()
    val nowPlaying by app.folio.ui.LocalContainer.current.musicPlayer.state.collectAsStateWithLifecycle()
    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.import(uris.map { it.toString() })
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
        contentPadding = PaddingValues(
            start = spacing.screenPadding,
            end = spacing.screenPadding,
            top = 12.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGap),
    ) {
        if (settings.home.greeting) {
            item(key = "greeting") {
                Text(
                    text = stringResource(greetingRes()),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Only take a list slot while music is queued, so an idle player leaves no gap.
        if (nowPlaying.hasQueue) {
            item(key = "now-playing") { app.folio.ui.screens.music.NowPlayingBar() }
        }

        settings.home.sections.forEach { section ->
            when (section) {
                HomeSection.CONTINUE_READING -> if (continueReading.isNotEmpty()) {
                    item(key = "continue") {
                        Column {
                            SectionHeader(
                                title = stringResource(R.string.home_continue_reading),
                                actionLabel = stringResource(R.string.see_all),
                                onAction = onSeeLibrary,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(continueReading, key = { it.id }) { book ->
                                    ContinueCard(book, onOpenBook, onBookDetails)
                                }
                            }
                        }
                    }
                }

                HomeSection.READING_GOAL -> if (stats.goalMinutes != null) {
                    item(key = "goal") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onStats),
                        ) {
                            Column(Modifier.padding(spacing.cardPadding)) {
                                Text(
                                    text = stringResource(R.string.home_goal),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${Format.shortDuration(stats.todayMs)} / ${stats.goalMinutes}m",
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Spacer(Modifier.height(10.dp))
                                ProgressBar(stats.goalProgress, Modifier.fillMaxWidth(), height = 6.dp)
                            }
                        }
                    }
                }

                HomeSection.STREAK -> if (stats.streak > 0) {
                    item(key = "streak") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable(onClick = onStats)
                                .padding(spacing.cardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.LocalFireDepartment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column {
                                Text(
                                    text = pluralStringResource(R.plurals.home_streak_days, stats.streak, stats.streak),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    text = "${stringResource(R.string.stats_today)}: " +
                                        Format.duration(context, stats.todayMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                HomeSection.RECENTLY_ADDED -> if (recentlyAdded.isNotEmpty()) {
                    item(key = "recent") {
                        Column {
                            SectionHeader(
                                title = stringResource(R.string.home_recently_added),
                                actionLabel = stringResource(R.string.see_all),
                                onAction = onSeeLibrary,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recentlyAdded, key = { it.id }) { book ->
                                    Column(
                                        Modifier
                                            .width(104.dp)
                                            .clickable { onOpenBook(book.id) },
                                    ) {
                                        BookCover(
                                            title = book.title,
                                            coverPath = book.coverPath,
                                            format = book.format,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(0.66f),
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = book.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HomeSection.COLLECTIONS -> if (collections.isNotEmpty()) {
                    item(key = "collections") {
                        Column {
                            SectionHeader(
                                title = stringResource(R.string.home_collections),
                                actionLabel = stringResource(R.string.see_all),
                                onAction = onCollections,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(collections, key = { it.collection.id }) { entry ->
                                    Column(
                                        Modifier
                                            .width(150.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable { onCollection(entry.collection.id) }
                                            .padding(14.dp),
                                    ) {
                                        Text(
                                            text = entry.collection.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = pluralStringResource(R.plurals.library_books_count, entry.bookCount, entry.bookCount),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HomeSection.ACTIVITY -> item(key = "activity") {
                    Column {
                        SectionHeader(
                            title = stringResource(R.string.home_activity),
                            actionLabel = stringResource(R.string.see_all),
                            onAction = onStats,
                        )
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(spacing.cardPadding)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    StatColumn(
                                        stringResource(R.string.stats_today),
                                        Format.duration(context, stats.todayMs),
                                    )
                                    StatColumn(
                                        stringResource(R.string.stats_this_week),
                                        Format.duration(context, stats.weekMs),
                                    )
                                }
                                Spacer(Modifier.height(14.dp))
                                WeekChart(stats.lastSevenDays.map { it.second })
                            }
                        }
                    }
                }

                HomeSection.POMODORO -> item(key = "pomodoro") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onPomodoro),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(spacing.cardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.home_pomodoro),
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        text = if (pomodoro.active) {
                                            Format.timer(remaining)
                                        } else {
                                            pluralStringResource(
                                                R.plurals.pomodoro_today_sessions,
                                                pomodoro.completedFocusSessions,
                                                pomodoro.completedFocusSessions,
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (!pomodoro.active) {
                                FilledTonalIconButton(
                                    onClick = { askNotifications(); viewModel.startFocus() },
                                    modifier = Modifier.size(56.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        // The label stays available to screen readers.
                                        contentDescription = stringResource(R.string.action_start_focus),
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        if (pomodoro.running) viewModel.pausePomodoro() else viewModel.resumePomodoro()
                                    },
                                ) {
                                    Text(
                                        stringResource(
                                            if (pomodoro.running) R.string.pomodoro_pause else R.string.pomodoro_resume,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }

                HomeSection.QUEUE -> if (queue.isNotEmpty()) {
                    item(key = "queue") {
                        Column {
                            SectionHeader(
                                title = stringResource(R.string.home_queue),
                                actionLabel = stringResource(R.string.see_all),
                                onAction = onQueue,
                            )
                            queue.take(4).forEachIndexed { index, book ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onOpenBook(book.id) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp),
                                    )
                                    BookCover(
                                        title = book.title,
                                        coverPath = book.coverPath,
                                        format = book.format,
                                        modifier = Modifier
                                            .width(34.dp)
                                            .aspectRatio(0.66f),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (continueReading.isEmpty() && recentlyAdded.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.empty_library_title),
                    message = stringResource(R.string.empty_library_message),
                    actionLabel = stringResource(R.string.action_import),
                    onAction = { openFiles.launch(arrayOf("*/*")) },
                )
            }
        }
    }
}

@Composable
private fun ContinueCard(
    book: LibraryBook,
    onOpen: (Long) -> Unit,
    onDetails: (Long) -> Unit,
) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpen(book.id) }
            .padding(bottom = 4.dp),
    ) {
        BookCover(
            title = book.title,
            coverPath = book.coverPath,
            format = book.format,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        ProgressBar(book.progress, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.progress_percent, Format.percent(book.progress)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onDetails(book.id) },
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun WeekChart(values: List<Long>, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        if (values.isEmpty()) return@Canvas
        val gap = 10f
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        values.forEachIndexed { index, value ->
            val fraction = value.toFloat() / max
            val barHeight = (size.height * fraction).coerceAtLeast(3f)
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = track,
                topLeft = Offset(left, 0f),
                size = Size(barWidth, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
            drawRoundRect(
                color = if (value > 0) accent else Color.Transparent,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
        }
    }
}

private fun greetingRes(): Int {
    val hour = LocalTime.now().hour
    return when {
        hour < 5 -> R.string.greeting_night
        hour < 12 -> R.string.greeting_morning
        hour < 18 -> R.string.greeting_afternoon
        hour < 22 -> R.string.greeting_evening
        else -> R.string.greeting_night
    }
}

@Composable
private fun CircleDot() {
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}
