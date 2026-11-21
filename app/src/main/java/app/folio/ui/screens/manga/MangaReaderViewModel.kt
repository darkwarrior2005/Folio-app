package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.reader.comic.ComicSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class ReaderLoadState {
    object Loading : ReaderLoadState()
    data class Loaded(val comic: ComicSource) : ReaderLoadState()
    data class Failed(val reason: String) : ReaderLoadState()
}

class MangaReaderViewModel(private val container: AppContainer, private val mangaId: Long, private val chapterId: Long) : ViewModel() {

    private val _state = MutableStateFlow<ReaderLoadState>(ReaderLoadState.Loading)
    val state: StateFlow<ReaderLoadState> = _state

    private var comic: ComicSource? = null

    init {
        viewModelScope.launch {
            val manga = container.manga.observe(mangaId).first() ?: return@launch
            val chapter = container.manga.observeChapters(mangaId).first()
                .firstOrNull { it.id == chapterId } ?: return@launch
            val source = container.sourceManager.get(manga.sourceId) as? HttpSource
            if (source == null) {
                _state.value = ReaderLoadState.Failed("This source is not available")
                return@launch
            }
            try {
                container.mangaAccess.requireEnabled()
                val result = container.mangaChapterLoader.open(source, manga.title, chapter)
                result.onSuccess {
                    comic = it
                    _state.value = ReaderLoadState.Loaded(it)
                }.onFailure {
                    _state.value = ReaderLoadState.Failed(it.message ?: "Failed to open chapter")
                }
            } catch (e: Exception) {
                _state.value = ReaderLoadState.Failed(e.message ?: "Failed to open chapter")
            }
        }
    }

    fun markRead() {
        viewModelScope.launch { container.manga.setChaptersRead(listOf(chapterId), true) }
    }

    fun setProgress(page: Int, pages: Int) {
        viewModelScope.launch { container.manga.setProgress(chapterId, page, pages) }
    }

    override fun onCleared() {
        comic?.close()
    }
}
