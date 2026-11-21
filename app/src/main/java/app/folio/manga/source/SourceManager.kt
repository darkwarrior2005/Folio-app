package app.folio.manga.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Registry of currently usable sources. A source whose extension was uninstalled or disabled
 * isn't removed outright - it's replaced with a [StubSource] so a manga that came from it still
 * shows something sensible instead of vanishing.
 */
class SourceManager {

    private val _sources = MutableStateFlow<Map<Long, Source>>(emptyMap())
    val sources: StateFlow<Map<Long, Source>> = _sources

    /** [disabledPackages] sources are left out of the registry entirely, not stubbed. */
    fun setSources(all: List<Pair<String, Source>>, disabledPackages: Set<String>) {
        _sources.update {
            all.filterNot { (pkg, _) -> pkg in disabledPackages }
                .associate { (_, source) -> source.id to source }
        }
    }

    fun get(id: Long): Source? = _sources.value[id]

    fun getCatalogueSource(id: Long): CatalogueSource? = get(id) as? CatalogueSource

    fun getOrStub(id: Long, lastKnownName: String?): Source =
        get(id) ?: StubSource(id, lastKnownName ?: "Unknown", unavailableMessage = UNINSTALLED)

    fun markUnavailable(id: Long, name: String, disabledByUser: Boolean) {
        if (get(id) != null) return
        _sources.update { it + (id to StubSource(id, name, if (disabledByUser) DISABLED else UNINSTALLED)) }
    }

    companion object {
        const val UNINSTALLED = "The extension for this source is not installed"
        const val DISABLED = "This source's extension is turned off"
    }
}

/** A placeholder for a source whose extension is missing or disabled. */
class StubSource(
    override val id: Long,
    override val name: String,
    val unavailableMessage: String,
) : CatalogueSource {
    override val lang: String = ""
    override val supportsLatest: Boolean = false
}
