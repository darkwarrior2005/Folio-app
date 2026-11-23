package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.data.db.MangaDownloadEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One queued chapter, with the names needed to show it. */
data class QueueRow(
    val download: MangaDownloadEntity,
    val mangaTitle: String,
    val chapterName: String,
)

class DownloadQueueViewModel(private val container: AppContainer) : ViewModel() {

    private val _rows = MutableStateFlow<List<QueueRow>>(emptyList())
    val rows: StateFlow<List<QueueRow>> = _rows.asStateFlow()

    val queueSize: StateFlow<Int> = container.downloads.observeQueueSize()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val wifiOnly: StateFlow<Boolean> = container.settings.settings
        .map { it.manga.downloadOverWifiOnly }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        viewModelScope.launch {
            container.downloads.observeQueue().collect { downloads ->
                _rows.value = downloads.map { download ->
                    QueueRow(
                        download = download,
                        mangaTitle = container.manga.get(download.mangaId)?.title.orEmpty(),
                        chapterName = container.manga.chapter(download.chapterId)?.name.orEmpty(),
                    )
                }
            }
        }
    }

    fun cancel(chapterId: Long) {
        viewModelScope.launch { container.downloads.cancel(listOf(chapterId)) }
    }

    fun clearAll() {
        viewModelScope.launch { container.downloads.clearQueue() }
    }
}
