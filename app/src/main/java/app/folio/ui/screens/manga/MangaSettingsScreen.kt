package app.folio.ui.screens.manga

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.R
import app.folio.data.settings.MangaSettings
import app.folio.ui.components.NavigationRow
import app.folio.ui.components.SettingsSectionHeader
import app.folio.ui.components.SettingsScaffold
import app.folio.ui.components.SliderRow
import app.folio.ui.components.SwitchRow
import app.folio.ui.folioViewModel
import app.folio.ui.screens.lock.rememberDeviceAuth
import app.folio.ui.util.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MangaSettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<MangaSettings> = container.settings.settings
        .map { it.manga }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaSettings())

    private val _storageUsed = MutableStateFlow(0L)
    val storageUsed: StateFlow<Long> = _storageUsed.asStateFlow()

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch { _storageUsed.value = container.downloads.storageUsed() }
    }

    fun update(transform: (MangaSettings) -> MangaSettings) {
        viewModelScope.launch {
            container.settings.update { current -> current.copy(manga = transform(current.manga)) }
        }
    }
}

/**
 * The manga library's settings, including the switch that decides whether Folio uses the
 * network at all.
 */
@Composable
fun MangaSettingsScreen(
    onBack: () -> Unit,
    onRepos: () -> Unit,
    onExtensions: () -> Unit,
    viewModel: MangaSettingsViewModel = folioViewModel { MangaSettingsViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageUsed by viewModel.storageUsed.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.settings_manga), onBack = onBack) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.manga_network_note),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }

        SwitchRow(
            label = stringResource(R.string.manga_setting_sources),
            checked = settings.sourcesEnabled,
        ) { enabled -> viewModel.update { it.copy(sourcesEnabled = enabled) } }

        SwitchRow(
            label = stringResource(R.string.manga_setting_adult),
            checked = settings.allowAdultSources,
        ) { allowed -> viewModel.update { it.copy(allowAdultSources = allowed) } }

        // Turning the lock off is itself protected, or anyone holding the phone could do it.
        val confirmOwner = rememberDeviceAuth(stringResource(R.string.manga_setting_lock_extensions))
        SwitchRow(
            label = stringResource(R.string.manga_setting_lock_extensions),
            checked = settings.lockExtensions,
        ) { lock ->
            if (lock) {
                viewModel.update { it.copy(lockExtensions = true) }
            } else {
                confirmOwner { viewModel.update { it.copy(lockExtensions = false) } }
            }
        }

        NavigationRow(stringResource(R.string.manga_extensions), null, onExtensions)
        NavigationRow(stringResource(R.string.manga_repos), null, onRepos)

        SettingsSectionHeader(
            title = stringResource(R.string.manga_setting_downloads),
            modifier = Modifier.padding(top = 18.dp),
        )

        SwitchRow(
            label = stringResource(R.string.manga_setting_wifi_only),
            checked = settings.downloadOverWifiOnly,
        ) { wifi -> viewModel.update { it.copy(downloadOverWifiOnly = wifi) } }

        SliderRow(
            label = stringResource(R.string.manga_setting_parallel),
            value = settings.parallelPageDownloads.toFloat(),
            range = 1f..5f,
            display = settings.parallelPageDownloads.toString(),
        ) { value -> viewModel.update { it.copy(parallelPageDownloads = value.toInt()) } }

        SwitchRow(
            label = stringResource(R.string.manga_setting_cbz),
            checked = settings.saveChaptersAsCbz,
        ) { cbz -> viewModel.update { it.copy(saveChaptersAsCbz = cbz) } }

        SwitchRow(
            label = stringResource(R.string.manga_setting_comicinfo),
            checked = settings.writeComicInfo,
        ) { write -> viewModel.update { it.copy(writeComicInfo = write) } }

        SliderRow(
            label = stringResource(R.string.manga_setting_read_ahead),
            value = settings.downloadAheadChapters.toFloat(),
            range = 0f..5f,
            display = settings.downloadAheadChapters.toString(),
        ) { value -> viewModel.update { it.copy(downloadAheadChapters = value.toInt()) } }

        SwitchRow(
            label = stringResource(R.string.manga_setting_delete_after_read),
            checked = settings.deleteChapterAfterRead,
        ) { delete -> viewModel.update { it.copy(deleteChapterAfterRead = delete) } }

        Text(
            text = stringResource(R.string.manga_storage_used, Format.fileSize(storageUsed)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 14.dp),
        )
    }
}
