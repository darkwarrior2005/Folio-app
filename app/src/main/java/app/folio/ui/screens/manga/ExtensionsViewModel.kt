package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.data.db.ExtensionRepoEntity
import app.folio.manga.extension.model.Extension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExtensionsViewModel(private val container: AppContainer) : ViewModel() {

    private val extensions get() = container.extensions

    val installed: StateFlow<List<Extension.Installed>> = extensions.installed
    val available: StateFlow<List<Extension.Available>> = extensions.available
    val refreshing: StateFlow<Boolean> = extensions.refreshing

    val repos: StateFlow<List<ExtensionRepoEntity>> = container.extensionRepos.observeRepos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val sourcesEnabled: StateFlow<Boolean> = container.mangaAccess.observeEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val hiddenLanguages: StateFlow<Set<String>> = container.observeHiddenLanguages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val disabledExtensions: StateFlow<Set<String>> = container.settings.settings
        .map { it.manga.disabledExtensions }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Null until the setting has loaded, so the screen does not flash before asking to unlock. */
    val lockRequired: StateFlow<Boolean?> = container.settings.settings
        .map { settings -> settings.manga.lockExtensions as Boolean? }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setEnabled(pkgName: String, enabled: Boolean) {
        viewModelScope.launch { extensions.setEnabled(pkgName, enabled) }
    }

    fun setHiddenLanguages(hidden: Set<String>) {
        viewModelScope.launch { container.setHiddenLanguages(hidden) }
    }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Set when a refresh reached some but not all repositories: (failed count, total count). */
    private val _repoRefreshIssue = MutableStateFlow<Pair<Int, Int>?>(null)
    val repoRefreshIssue: StateFlow<Pair<Int, Int>?> = _repoRefreshIssue.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            extensions.refreshInstalled()
            if (container.mangaAccess.enabled()) {
                extensions.refreshAvailable()
                    .onSuccess { outcome ->
                        if (outcome.failedRepoNames.isNotEmpty()) {
                            _repoRefreshIssue.value = outcome.failedRepoNames.size to outcome.totalRepos
                        }
                    }
                    .onFailure { error ->
                        _message.value = error.message ?: "Could not reach the repositories"
                    }
            }
        }
    }

    fun clearRepoRefreshIssue() {
        _repoRefreshIssue.value = null
    }

    fun install(extension: Extension.Available) {
        viewModelScope.launch {
            extensions.install(extension).onFailure { error ->
                _message.value = error.message ?: "The install could not be started"
            }
        }
    }

    fun uninstall(pkgName: String) {
        extensions.uninstall(pkgName)
    }

    fun accept(extension: Extension.NotLoaded) {
        viewModelScope.launch { extensions.acceptAndLoad(extension) }
    }

    fun revokeTrust(pkgName: String) {
        viewModelScope.launch { extensions.revokeTrust(pkgName) }
    }

    fun addRepo(url: String) {
        viewModelScope.launch {
            container.extensionRepos.addRepo(url)
                .onSuccess { refresh() }
                .onFailure { error ->
                    _message.value = error.message ?: "That repository could not be read"
                }
        }
    }

    fun removeRepo(baseUrl: String) {
        viewModelScope.launch {
            container.extensionRepos.removeRepo(baseUrl)
            refresh()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /** Installed extensions a repository offers at a higher version. */
    fun updates(): List<Extension.Available> = extensions.updates()
}
