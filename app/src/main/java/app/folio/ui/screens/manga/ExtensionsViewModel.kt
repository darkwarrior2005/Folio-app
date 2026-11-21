package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.manga.extension.AvailableExtension
import app.folio.manga.extension.InstalledExtension
import app.folio.manga.extension.InstallStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExtensionsUiState(
    val installed: List<InstalledExtension> = emptyList(),
    val available: List<AvailableExtension> = emptyList(),
    val failedRepos: List<String> = emptyList(),
    val totalRepos: Int = 0,
    val loading: Boolean = false,
)

class ExtensionsViewModel(private val container: AppContainer) : ViewModel() {

    private val manager = container.extensionManager
    private val _state = MutableStateFlow(ExtensionsUiState())
    val state: StateFlow<ExtensionsUiState> = _state

    val repos = container.database.manga().observeRepos()

    val disabledExtensions: StateFlow<Set<String>> = container.settings.settings
        .map { it.manga.disabledExtensions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch { manager.refreshInstalled() }
        viewModelScope.launch {
            manager.installed.collect { list -> _state.update { it.copy(installed = list) } }
        }
        refreshAvailable()
    }

    fun refreshAvailable() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val result = manager.fetchAvailable()
            _state.update {
                it.copy(
                    available = result.extensions,
                    failedRepos = result.failedRepoNames,
                    totalRepos = result.totalRepos,
                    loading = false,
                )
            }
        }
    }

    fun addRepo(url: String) {
        viewModelScope.launch {
            manager.addRepo(url)
            refreshAvailable()
        }
    }

    fun removeRepo(id: Long) = viewModelScope.launch { manager.removeRepo(id) }

    fun install(extension: AvailableExtension, onStep: (InstallStep) -> Unit) {
        viewModelScope.launch {
            manager.installer.install(extension).collect { step ->
                onStep(step)
                if (step is InstallStep.Success) {
                    manager.refreshInstalled()
                }
            }
        }
    }

    fun uninstall(packageName: String) = manager.installer.uninstall(packageName)

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { manager.setExtensionEnabled(packageName, enabled) }
    }
}
