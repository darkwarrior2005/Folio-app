package app.folio.ui.screens.manga

import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.folio.data.settings.AppSettings
import app.folio.data.settings.MangaSettings
import app.folio.ui.LocalContainer
import app.folio.ui.screens.settings.SwitchRow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** The sources switch, download preferences and the extension-lock switch. */
@Composable
fun MangaSettingsScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val manga = settings.manga

    fun update(transform: (MangaSettings) -> MangaSettings) {
        scope.launch { container.settings.update { it.copy(manga = transform(it.manga)) } }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Manga sources") }) }) { padding ->
        Column(Modifier.fillMaxSize()) {
            SwitchRow("Manga sources", manga.sourcesEnabled) { update { m -> m.copy(sourcesEnabled = it) } }
            SwitchRow("Download over Wi-Fi only", manga.downloadOverWifiOnly) { update { m -> m.copy(downloadOverWifiOnly = it) } }
            StepperRow(
                "Parallel page downloads",
                manga.parallelPageDownloads,
                1..6,
            ) { update { m -> m.copy(parallelPageDownloads = it) } }
            SwitchRow("Save chapters as CBZ", manga.saveChaptersAsCbz) { update { m -> m.copy(saveChaptersAsCbz = it) } }
            SwitchRow("Write ComicInfo.xml", manga.writeComicInfo) { update { m -> m.copy(writeComicInfo = it) } }
            SwitchRow("Delete chapter after read", manga.deleteChapterAfterRead) { update { m -> m.copy(deleteChapterAfterRead = it) } }
            SwitchRow("Lock Extensions screen", manga.lockExtensions) { update { m -> m.copy(lockExtensions = it) } }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > range.first) onValueChange(value - 1) }) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Remove, contentDescription = "Decrease")
            }
            Text(value.toString())
            IconButton(onClick = { if (value < range.last) onValueChange(value + 1) }) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Add, contentDescription = "Increase")
            }
        }
    }
}
