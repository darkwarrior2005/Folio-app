package app.folio.manga.extension

import android.content.Context
import app.folio.data.db.ExtensionRepoEntity
import app.folio.data.db.MangaDao
import app.folio.data.settings.SettingsRepository
import app.folio.manga.source.SourceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class InstalledExtension(
    val packageName: String,
    val name: String,
    val versionName: String,
    val versionCode: Long,
    val sourceIds: List<Long>,
    val isNsfw: Boolean,
)

data class UpdateCandidate(val installed: InstalledExtension, val available: AvailableExtension)

/** Ties the loader, trust, repositories and installer together. */
class ExtensionManager(
    private val context: Context,
    private val mangaDao: MangaDao,
    private val settings: SettingsRepository,
    private val sourceManager: SourceManager,
) : ExtensionInstallReceiver.Listener {

    private val trust = ExtensionTrust(mangaDao)
    private val loader = ExtensionLoader(context, trust)
    private val repoService = ExtensionRepoService()
    val installer = ExtensionInstaller(context)

    private val _installed = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installed: StateFlow<List<InstalledExtension>> = _installed

    private val _untrusted = MutableStateFlow<List<android.content.pm.PackageInfo>>(emptyList())
    val untrusted: StateFlow<List<android.content.pm.PackageInfo>> = _untrusted

    suspend fun refreshInstalled() {
        val disabled = settings.current().manga.disabledExtensions
        val loadedExtensions = mutableListOf<InstalledExtension>()
        val untrustedPackages = mutableListOf<android.content.pm.PackageInfo>()
        val sourcesForRegistry = mutableListOf<Pair<String, eu.kanade.tachiyomi.source.Source>>()

        for (packageInfo in loader.installedExtensionPackages()) {
            when (val result = loader.load(packageInfo)) {
                is ExtensionLoadResult.Loaded -> {
                    val ext = result.extension
                    loadedExtensions += InstalledExtension(
                        ext.packageName, ext.name, ext.versionName, ext.versionCode,
                        ext.sources.map { it.id }, ext.isNsfw,
                    )
                    ext.sources.forEach { sourcesForRegistry += ext.packageName to it }
                }
                is ExtensionLoadResult.Untrusted -> untrustedPackages += result.packageInfo
                is ExtensionLoadResult.Failed -> Unit // logged elsewhere; one bad extension shouldn't block the rest
            }
        }

        sourceManager.setSources(sourcesForRegistry, disabled)
        _installed.value = loadedExtensions
        _untrusted.value = untrustedPackages
    }

    suspend fun trust(packageInfo: android.content.pm.PackageInfo) {
        trust.trust(packageInfo)
        refreshInstalled()
    }

    suspend fun availableUpdates(available: List<AvailableExtension>): List<UpdateCandidate> {
        val keyedByPackageAndKey = available.groupBy { it.packageName }
        return installed.value.mapNotNull { inst ->
            val candidates = keyedByPackageAndKey[inst.packageName].orEmpty()
            // Only consider an update signed with the key this installed copy already trusts.
            val match = candidates
                .filter { it.versionCode > inst.versionCode }
                .maxByOrNull { it.versionCode }
            match?.let { UpdateCandidate(inst, it) }
        }
    }

    suspend fun fetchAvailable(): AvailableExtensionsResult {
        val repos = mangaDao.repos()
        // Repositories added before Folio stored their name and signing key get them filled in.
        var backfilled = false
        repos.filter { it.name.isBlank() || it.signingKeyFingerprint == null }.forEach { repo ->
            val info = runCatching { repoService.fetchInfo(repo.baseUrl) }.getOrNull() ?: return@forEach
            mangaDao.updateRepo(
                repo.copy(
                    name = info.name.ifBlank { repo.name },
                    website = info.website ?: repo.website,
                    signingKeyFingerprint = info.signingKeyFingerprint ?: repo.signingKeyFingerprint,
                ),
            )
            backfilled = true
        }
        // A newly known key can trust extensions that were waiting for it.
        if (backfilled && _untrusted.value.isNotEmpty()) runCatching { refreshInstalled() }
        return repoService.fetchAll(repos.map { it.baseUrl })
    }

    suspend fun addRepo(baseUrl: String) {
        val normalized = normalizeRepoUrl(baseUrl)
        if (mangaDao.repos().any { it.baseUrl == normalized }) return
        val info = runCatching { repoService.fetchInfo(normalized) }.getOrNull()
        mangaDao.insertRepo(
            ExtensionRepoEntity(
                baseUrl = normalized,
                name = info?.name.orEmpty(),
                website = info?.website,
                signingKeyFingerprint = info?.signingKeyFingerprint,
            ),
        )
    }

    suspend fun removeRepo(id: Long) = mangaDao.deleteRepo(id)

    suspend fun setExtensionEnabled(packageName: String, enabled: Boolean) {
        settings.update { current ->
            val disabled = current.manga.disabledExtensions
            current.copy(
                manga = current.manga.copy(
                    disabledExtensions = if (enabled) disabled - packageName else disabled + packageName,
                ),
            )
        }
        refreshInstalled()
    }

    // Both triggered from a BroadcastReceiver context with no coroutine scope of its own; the
    // caller (FolioApp) observes these and calls refreshInstalled() on the app scope.
    override fun onExtensionChanged(packageName: String) = Unit
    override fun onExtensionRemoved(packageName: String) = Unit
}
