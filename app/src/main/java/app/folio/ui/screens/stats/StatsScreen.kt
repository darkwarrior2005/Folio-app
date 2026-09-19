package app.folio.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.data.db.GoalType
import app.folio.ui.components.ProgressBar
import app.folio.ui.components.SectionHeader
import app.folio.ui.components.TextInputDialog
import app.folio.ui.folioViewModel
import app.folio.ui.screens.home.WeekChart
import app.folio.ui.theme.LocalSpacing
import app.folio.ui.util.Format

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = folioViewModel { StatsViewModel(it) },
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    var editingGoal by remember { mutableStateOf<GoalType?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.stats_title)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(
                start = spacing.screenPadding,
                end = spacing.screenPadding,
                bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "totals") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile(stringResource(R.string.stats_total_books), stats.totalBooks.toString())
                    StatTile(stringResource(R.string.stats_finished), stats.finished.toString())
                    StatTile(stringResource(R.string.stats_reading), stats.reading.toString())
                    StatTile(stringResource(R.string.stats_unread), stats.unread.toString())
                }
            }

            item(key = "time") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(spacing.cardPadding)) {
                        Text(
                            text = stringResource(R.string.stats_total_time),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Format.duration(context, stats.totalTimeMs),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            SmallStat(
                                stringResource(R.string.stats_today),
                                Format.duration(context, stats.todayMs),
                            )
                            SmallStat(
                                stringResource(R.string.stats_this_week),
                                Format.duration(context, stats.weekMs),
                            )
                            SmallStat(
                                stringResource(R.string.stats_average_session),
                                Format.shortDuration(stats.averageSessionMs),
                            )
                        }
                    }
                }
            }

            item(key = "week") {
                Column {
                    SectionHeader(stringResource(R.string.stats_last_7_days))
                    WeekChart(stats.lastSevenDays.map { it.second })
                }
            }

            item(key = "month") {
                Column {
                    SectionHeader(stringResource(R.string.stats_last_30_days))
                    WeekChart(stats.lastThirtyDays.map { it.second })
                }
            }

            item(key = "streak") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatTile("🔥 ${stringResource(R.string.stats_streak)}", stats.streak.toString())
                    StatTile(stringResource(R.string.stats_this_month), stats.finishedThisMonth.toString())
                    StatTile(stringResource(R.string.stats_this_year), stats.finishedThisYear.toString())
                    StatTile(stringResource(R.string.stats_pages_read), stats.pagesRead.toString())
                }
            }

            if (stats.topCategories.isNotEmpty()) {
                item(key = "categories") {
                    Column {
                        SectionHeader(stringResource(R.string.stats_top_categories))
                        val max = stats.topCategories.maxOf { it.second }.coerceAtLeast(1)
                        stats.topCategories.forEach { (name, ms) ->
                            Column(Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = Format.shortDuration(ms),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                ProgressBar(ms.toFloat() / max, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }

            item(key = "goals") {
                Column {
                    SectionHeader(stringResource(R.string.stats_goals))
                    GoalType.entries.forEach { type ->
                        val goal = goals.firstOrNull { it.type == type }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(type.label(), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = goal?.target?.toString() ?: "—",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { editingGoal = type }) {
                                Text(stringResource(R.string.action_edit))
                            }
                            Switch(
                                checked = goal?.enabled == true,
                                onCheckedChange = { enabled ->
                                    viewModel.setGoal(type, goal?.target ?: defaultTarget(type), enabled)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    editingGoal?.let { type ->
        val goal = goals.firstOrNull { it.type == type }
        TextInputDialog(
            title = type.label(),
            label = stringResource(R.string.goal_enabled),
            initialValue = (goal?.target ?: defaultTarget(type)).toString(),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { value ->
                value.toIntOrNull()?.let { viewModel.setGoal(type, it, true) }
                editingGoal = null
            },
            onDismiss = { editingGoal = null },
        )
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.width(150.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun GoalType.label(): String = stringResource(
    when (this) {
        GoalType.MINUTES_PER_DAY -> R.string.goal_minutes_per_day
        GoalType.PAGES_PER_DAY -> R.string.goal_pages_per_day
        GoalType.BOOKS_PER_MONTH -> R.string.goal_books_per_month
        GoalType.BOOKS_PER_YEAR -> R.string.goal_books_per_year
    },
)

private fun defaultTarget(type: GoalType): Int = when (type) {
    GoalType.MINUTES_PER_DAY -> 30
    GoalType.PAGES_PER_DAY -> 20
    GoalType.BOOKS_PER_MONTH -> 2
    GoalType.BOOKS_PER_YEAR -> 24
}
