package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.data.db.ExtensionRepoEntity
import app.folio.manga.extension.AvailableExtension
import app.folio.manga.extension.InstallStep
import app.folio.manga.extension.InstalledExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** Install progress per package name, shown on that extension's row. */
    val installSteps: Map<String, InstallStep> = emptyMap(),
)

class ExtensionsViewModel(private val container: AppContainer) : ViewModel() {

    private val manager = container.extensionManager
    private val _state = MutableStateFlow(ExtensionsUiState())
    val state: StateFlow<ExtensionsUiState> = _state

    val repos: StateFlow<List<ExtensionRepoEntity>> = container.database.manga().observeRepos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The master switch: nothing here loads or connects while it's off. Null until known. */
    val sourcesEnabled: StateFlow<Boolean?> = container.settings.settings
        .map { it.manga.sourcesEnabled }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val disabledExtensions: StateFlow<Set<String>> = container.settings.settings
        .map { it.manga.disabledExtensions }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch {
            manager.installed.collect { list -> _state.update { it.copy(installed = list) } }
        }
        viewModelScope.launch {
            sourcesEnabled.collect { enabled -> if (enabled == true) refresh() }
        }
    }

    fun turnOnSources() {
        viewModelScope.launch {
            container.settings.update { it.copy(manga = it.manga.copy(sourcesEnabled = true)) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            if (!container.mangaAccess.isEnabled()) return@launch
            _state.update { it.copy(loading = true) }
            runCatching { manager.refreshInstalled() }
            val result = runCatching { manager.fetchAvailable() }.getOrNull()
            _state.update {
                it.copy(
                    available = result?.extensions ?: it.available,
                    failedRepos = result?.failedRepoNames ?: it.failedRepos,
                    totalRepos = result?.totalRepos ?: it.totalRepos,
                    loading = false,
                )
            }
        }
    }

    fun addRepo(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            manager.addRepo(url)
            refresh()
        }
    }

    fun removeRepo(id: Long) {
        viewModelScope.launch {
            manager.removeRepo(id)
            refresh()
        }
    }

    fun install(extension: AvailableExtension) {
        val pkg = extension.packageName
        viewModelScope.launch {
            manager.installer.install(extension)
                .catch { e -> emit(InstallStep.Error(e.message ?: "Download failed")) }
                .collect { step ->
                    _state.update { it.copy(installSteps = it.installSteps + (pkg to step)) }
                    if (step is InstallStep.Success) runCatching { manager.refreshInstalled() }
                }
        }
    }

    fun uninstall(packageName: String) = manager.installer.uninstall(packageName)

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { manager.setExtensionEnabled(packageName, enabled) }
    }
}
