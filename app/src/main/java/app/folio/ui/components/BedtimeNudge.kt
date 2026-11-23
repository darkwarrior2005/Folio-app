package app.folio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.core.model.NightLight
import app.folio.data.settings.NightLightSettings
import app.folio.ui.icons.FolioIcons
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * A soft note at bedtime while Folio is open: your page is kept, sleep well. "10 more minutes"
 * brings it back later; "Good night" lets it rest until tomorrow. Shown over [content] without
 * taking it over, and never outside the three hours after bedtime.
 */
@Composable
fun BedtimeNudge(settings: NightLightSettings, content: @Composable () -> Unit) {
    var snoozedUntil by rememberSaveable { mutableLongStateOf(0L) }
    var dismissedNight by rememberSaveable { mutableLongStateOf(-1L) }

    val now by produceState(System.currentTimeMillis(), settings.bedtimeReminder) {
        while (settings.bedtimeReminder) {
            value = System.currentTimeMillis()
            delay(30_000)
        }
    }
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    val minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    // The night a nudge belongs to is the day bedtime fell on, so after midnight is still "tonight".
    val night = calendar.apply { if (minutes < settings.bedtimeMinutes) add(Calendar.DAY_OF_YEAR, -1) }
        .let { it.get(Calendar.YEAR) * 1_000L + it.get(Calendar.DAY_OF_YEAR) }
    val show = settings.bedtimeReminder &&
        NightLight.bedtimeDue(minutes, settings.bedtimeMinutes) &&
        now >= snoozedUntil &&
        dismissedNight != night

    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(
            visible = show,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        FolioIcons.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(top = 2.dp, end = 12.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.bedtime_title, NightLight.clock(settings.bedtimeMinutes)),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.bedtime_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            TextButton(onClick = { snoozedUntil = System.currentTimeMillis() + SNOOZE_MS }) {
                                Text(stringResource(R.string.bedtime_snooze))
                            }
                            Button(onClick = { dismissedNight = night }) { Text(stringResource(R.string.bedtime_good_night)) }
                        }
                    }
                }
            }
        }
    }
}

private const val SNOOZE_MS = 10 * 60_000L
