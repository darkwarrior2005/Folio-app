package app.folio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.folio.core.model.NightLight
import app.folio.data.settings.NightLightSettings
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Shows a card while Folio is open in the three hours after bedtime. "10 more minutes" snoozes;
 * "Good night" rests until the next local night (resets automatically once past bedtime again).
 */
@Composable
fun BedtimeNudge(
    settings: NightLightSettings,
    dismissedUntil: Long,
    onSnooze: (Long) -> Unit,
    onGoodNight: (Long) -> Unit,
    content: @Composable () -> Unit,
) {
    var nowMinutes by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMinutes = System.currentTimeMillis()
        }
    }

    val minutesOfDay = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
    val due = NightLight.bedtimeDue(settings, minutesOfDay) && System.currentTimeMillis() > dismissedUntil

    Box(Modifier.fillMaxSize()) {
        content()
        AnimatedVisibility(visible = due) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("It's past your bedtime")
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { onSnooze(System.currentTimeMillis() + 10 * 60_000) }) {
                                Text("10 more minutes")
                            }
                            Button(onClick = { onGoodNight(nextMidnightMillis()) }) {
                                Text("Good night")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun nextMidnightMillis(): Long {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, 1)
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}
