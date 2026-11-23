package app.folio.manga.extension

import app.folio.data.settings.SettingsRepository
import app.folio.manga.MangaAccess
import app.folio.manga.extension.model.ContentWarning
import app.folio.manga.extension.model.Extension
import app.folio.manga.source.SourceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How a refresh of the available-extensions list went: which repos (of how many) didn't answer. */
data class RefreshOutcome(val failedRepoNames: List<String>, val totalRepos: Int)

/**
 * Keeps the installed extensions, the sources they provide and the list of what the user's
 * repositories offer in step with each other.
 *
 * Adapted from Mihon (Apache-2.0).
 */
class ExtensionManager(
    private val loader: ExtensionLoader,
    private val trust: ExtensionTrust,
    private val repos: ExtensionRepoService,
    private val installer: ExtensionInstaller,
    private val sources: SourceManager,
    private val access: MangaAccess,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {

    private val _installed = MutableStateFlow<List<Extension.Installed>>(emptyList())
    val installed: StateFlow<List<Extension.Installed>> = _installed.asStateFlow()

    private val _available = MutableStateFlow<List<Extension.Available>>(emptyList())
    val available: StateFlow<List<Extension.Available>> = _available.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * Reads the installed extensions and registers the sources of the ones that loaded.
     *
     * Loading an extension runs its code, so this does nothing at all while manga sources are
     * turned off: no third-party class is instantiated until the user has opted in.
     */
    suspend fun refreshInstalled() {
        if (!access.enabled()) {
            _installed.value = emptyList()
            sources.setSources(emptyList())
            return
        }

        val extensions = loader.loadAll(allowedContentWarnings())
        _installed.value = extensions
        val disabled = settings.current().manga.disabledExtensions
        val (off, on) = extensions.filterIsInstance<Extension.Loaded>().partition { it.pkgName in disabled }
        sources.setSources(on.flatMap { it.sources }, off = off.flatMap { it.sources })
    }

    /**
     * Turns an installed extension on or off. Off, its sources leave Browse and searches and its
     * manga cannot be refreshed or read online until it is turned back on; nothing is uninstalled.
     */
    suspend fun setEnabled(pkgName: String, enabled: Boolean) {
        settings.update { current ->
            val disabled = current.manga.disabledExtensions
            current.copy(
                manga = current.manga.copy(
                    disabledExtensions = if (enabled) disabled - pkgName else disabled + pkgName,
                ),
            )
        }
        refreshInstalled()
    }

    /**
     * Reads every repository's index. Needs the network, so it does nothing while manga sources
     * are turned off.
     */
    suspend fun refreshAvailable(): Result<RefreshOutcome> {
        if (!access.enabled()) return Result.failure(app.folio.manga.SourcesDisabledException())
        _refreshing.value = true
        return try {
            val result = repos.availableExtensions()
            _available.value = result.extensions
            Result.success(RefreshOutcome(result.failedRepoNames, result.totalRepos))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            _refreshing.value = false
        }
    }

    /**
     * Accepts an extension the user was warned about, then loads it. The signature accepted is
     * the one that was shown to them, so an apk replaced in the meantime is refused again.
     */
    suspend fun acceptAndLoad(extension: Extension.NotLoaded) {
        val reason = extension.reason
        require(reason is Extension.NotLoaded.Reason.Untrusted) {
            "Only an untrusted extension can be accepted"
        }
        trust.trust(extension.pkgName, extension.versionCode, reason.signature)
        refreshInstalled()
    }

    /** Stops running an extension's code until the user accepts it again. */
    suspend fun revokeTrust(pkgName: String) {
        trust.revoke(pkgName)
        refreshInstalled()
    }

    suspend fun install(extension: Extension.Available): Result<Unit> = installer.install(extension)

    fun uninstall(pkgName: String) {
        installer.uninstall(pkgName)
    }

    /**
     * Extensions a repository offers at a higher version than the one installed, at most one per
     * package. Several repositories can offer the same package; only one signed with the key the
     * installed copy carries can update it (Android refuses the rest), so that one is preferred.
     */
    fun updates(): List<Extension.Available> {
        val installedByPkg = _installed.value.associateBy { it.pkgName }
        return _available.value
            .filter { candidate ->
                val current = installedByPkg[candidate.pkgName] ?: return@filter false
                candidate.versionCode > current.versionCode &&
                    (current.signatures.isEmpty() || candidate.repoSigningKey in current.signatures)
            }
            .groupBy { it.pkgName }
            .map { (_, offers) -> offers.maxBy { it.versionCode } }
    }

    /**
     * Follows the manga sources setting: loads the installed extensions once it is on, and drops
     * every registered source again if it is turned back off.
     */
    fun start() {
        scope.launch {
            access.observeEnabled().collect { refreshInstalled() }
        }
    }

    private suspend fun allowedContentWarnings(): Set<ContentWarning> =
        if (settings.current().manga.allowAdultSources) {
            ContentWarning.entries.toSet()
        } else {
            setOf(ContentWarning.SAFE)
        }
}
