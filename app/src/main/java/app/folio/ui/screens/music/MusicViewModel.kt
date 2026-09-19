package app.folio.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.PlaybackSource
import app.folio.data.db.TrackEntity
import app.folio.data.repo.MusicImportResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportSummary(val added: Int, val duplicates: Int, val unreadable: Int)

class MusicViewModel(private val container: AppContainer) : ViewModel() {
    private val music = container.music

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _tagFilter = MutableStateFlow<Long?>(null)
    val tagFilter: StateFlow<Long?> = _tagFilter.asStateFlow()

    val collections = music.collections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = music.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tracks: StateFlow<List<TrackEntity>> = combine(music.tracks, music.trackTags, tags, _query, _tagFilter) {
            tracks, links, tagList, query, tagId ->
        val tagsByTrack = links.groupBy({ it.trackId }, { it.tagId })
        val tagNames = tagList.associate { it.tag.id to it.tag.name }
        val needle = query.trim().lowercase()
        tracks.filter { track ->
            val trackTags = tagsByTrack[track.id].orEmpty()
            (tagId == null || tagId in trackTags) &&
                (needle.isEmpty() || listOfNotNull(
                    track.displayTitle, track.displayArtist, track.overrides.album ?: track.imported.album,
                ).plus(trackTags.mapNotNull { tagNames[it] }).any { it.lowercase().contains(needle) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _importSummary = MutableSharedFlow<ImportSummary>(extraBufferCapacity = 2)
    val importSummary: SharedFlow<ImportSummary> = _importSummary.asSharedFlow()

    fun setQuery(value: String) { _query.value = value }
    fun setTagFilter(id: Long?) { _tagFilter.value = id }

    fun importFiles(uris: List<String>) = viewModelScope.launch { report(uris.map { music.importUri(it) }) }
    fun importFolder(uri: String) = viewModelScope.launch { report(music.importFolder(uri)) }

    private fun report(results: List<MusicImportResult>) {
        _importSummary.tryEmit(
            ImportSummary(
                added = results.count { it is MusicImportResult.Added },
                duplicates = results.count { it is MusicImportResult.Duplicate },
                unreadable = results.count { it is MusicImportResult.Unreadable },
            ),
        )
    }

    fun play(tracks: List<TrackEntity>, index: Int) =
        container.musicPlayer.playTracks(tracks.filterNot { it.missing }, PlaybackSource.Library, startIndex = index)

    fun createCollection(name: String) = viewModelScope.launch { if (name.isNotBlank()) music.createCollection(name) }

    fun removeTracks(ids: List<Long>) = viewModelScope.launch { music.removeFromLibrary(ids) }
}
