package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.Manga
import app.folio.core.model.MangaChapter
import app.folio.core.model.NoChaptersException
import app.folio.manga.download.DownloadWorker
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RefreshError(val hadChaptersBefore: Boolean, val message: String)

private data class RefreshState(val refreshing: Boolean = false, val error: RefreshError? = null)

data class MangaDetailsUiState(
    val manga: Manga? = null,
    val chapters: List<MangaChapter> = emptyList(),
    val refreshing: Boolean = false,
    val refreshError: RefreshError? = null,
)

class MangaDetailsViewModel(private val container: AppContainer, private val mangaId: Long) : ViewModel() {

    private val refresh = MutableStateFlow(RefreshState())

    val state: StateFlow<MangaDetailsUiState> = combine(
        container.manga.observe(mangaId),
        container.manga.observeChapters(mangaId),
        refresh,
    ) { manga, chapters, refreshState ->
        MangaDetailsUiState(manga, chapters, refreshState.refreshing, refreshState.error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MangaDetailsUiState())

    fun refresh() {
        viewModelScope.launch {
            val manga = state.value.manga ?: return@launch
            val source = container.sourceManager.get(manga.sourceId) as? HttpSource ?: return@launch
            refresh.value = RefreshState(refreshing = true)
            try {
                container.mangaAccess.requireEnabled()
                val sManga = eu.kanade.tachiyomi.source.model.SManga.create().apply { url = manga.url }
                val fetched = source.getChapterList(sManga)
                container.manga.syncChapters(mangaId, manga.title, fetched)
                refresh.value = RefreshState(refreshing = false)
            } catch (e: NoChaptersException) {
                refresh.value = RefreshState(
                    refreshing = false,
                    error = RefreshError(
                        hadChaptersBefore = e.hadChapters,
                        message = if (e.hadChapters) {
                            "The source returned no chapters this time"
                        } else {
                            "No chapters found - this source may not have this series"
                        },
                    ),
                )
            } catch (e: Exception) {
                refresh.value = RefreshState(
                    refreshing = false,
                    error = RefreshError(state.value.chapters.isNotEmpty(), e.message ?: "Refresh failed"),
                )
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val manga = state.value.manga ?: return@launch
            container.manga.setFavorite(manga.id, !manga.favorite)
        }
    }

    fun setRead(chapterIds: List<Long>, read: Boolean) {
        viewModelScope.launch { container.manga.setChaptersRead(chapterIds, read) }
    }

    fun setBookmarked(chapterIds: List<Long>, bookmark: Boolean) {
        viewModelScope.launch { container.manga.setChaptersBookmarked(chapterIds, bookmark) }
    }

    fun download(chapterIds: List<Long>) {
        viewModelScope.launch {
            val manga = state.value.manga ?: return@launch
            val chapters = state.value.chapters.filter { it.id in chapterIds }
            container.downloadManager.enqueue(manga, chapters)
            DownloadWorker.schedule(container.appContext)
        }
    }

    fun deleteDownload(chapter: MangaChapter) {
        viewModelScope.launch {
            val manga = state.value.manga ?: return@launch
            container.downloadManager.deleteDownload(manga.sourceId, manga.title, chapter)
        }
    }

    fun isDownloaded(chapter: MangaChapter): Boolean {
        val manga = state.value.manga ?: return false
        return container.downloadManager.isDownloaded(manga.sourceId, manga.title, chapter.name)
    }
}
