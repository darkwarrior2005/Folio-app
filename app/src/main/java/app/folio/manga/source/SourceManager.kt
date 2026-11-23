package app.folio.manga.source

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * The sources available right now, by ID.
 *
 * Extensions come and go — uninstalled, updated, refused for being untrusted — while the library
 * keeps referring to their source IDs. Asking for a source that is not loaded therefore returns
 * a [StubSource] carrying whatever name is known, so the library can still list a manga whose
 * extension is missing instead of showing a bare number.
 */
class SourceManager {

    private val sources = MutableStateFlow<Map<Long, Source>>(emptyMap())

    /** Names last seen for a source ID, so a stub can be labelled after its extension is gone. */
    private val knownNames = mutableMapOf<Long, String>()

    /** Sources whose extension is installed but turned off by the user. */
    @Volatile
    private var turnedOff: Set<Long> = emptySet()

    val loadedSources: StateFlow<Map<Long, Source>> = sources.asStateFlow()

    val catalogueSources = sources.map { current ->
        current.values.filterIsInstance<CatalogueSource>().sortedBy { it.name }
    }

    /**
     * Replaces the registry with the sources of every loaded extension, plus any built-ins.
     * [off] are sources of extensions the user turned off: remembered by name, never registered.
     */
    fun setSources(all: List<Source>, off: List<Source> = emptyList()) {
        (all + off).forEach { knownNames[it.id] = it.name }
        turnedOff = off.mapTo(HashSet()) { it.id }
        sources.value = all.associateBy { it.id }
    }

    /** Why [id] cannot be used right now, worded for the user. */
    fun unavailableMessage(id: Long): String {
        val name = knownNames[id]
        return if (id in turnedOff) {
            "${name ?: "This source"} is turned off. Turn its extension back on in Extensions."
        } else {
            "The extension for ${name ?: "this source"} is not installed"
        }
    }

    fun get(id: Long): Source? = sources.value[id]

    /** The source, or a stub standing in for one whose extension is not loaded. */
    fun getOrStub(id: Long): Source = sources.value[id] ?: StubSource(id, knownNames[id], unavailableMessage(id))

    fun getCatalogue(id: Long): CatalogueSource? = sources.value[id] as? CatalogueSource

    fun isLoaded(id: Long): Boolean = sources.value.containsKey(id)
}

/**
 * Stands in for a source whose extension is not installed or not loaded. Every call fails with
 * [SourceNotInstalledException], which the UI turns into "install the extension for this source".
 */
class StubSource(
    override val id: Long,
    private val knownName: String?,
    private val reason: String? = null,
) : Source {

    override val name: String = knownName ?: "Source $id"

    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): MangasPage = throw SourceNotInstalledException(id, knownName, reason)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw SourceNotInstalledException(id, knownName, reason)

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw SourceNotInstalledException(id, knownName, reason)

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = throw SourceNotInstalledException(id, knownName, reason)

    override suspend fun getPageList(chapter: SChapter): List<Page> = throw SourceNotInstalledException(id, knownName, reason)

    override fun toString(): String = name
}

class SourceNotInstalledException(val sourceId: Long, val sourceName: String?, reason: String? = null) :
    IllegalStateException(reason ?: "No extension installed for source ${sourceName ?: sourceId}")
