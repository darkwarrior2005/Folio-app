package app.folio.ui.screens.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.data.settings.SavedFeed
import app.folio.data.settings.WebNovel
import app.folio.web.FeedItem
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class WebTaskState {
    object Idle : WebTaskState()
    object Working : WebTaskState()
    data class Done(val bookId: Long) : WebTaskState()
    data class Failed(val message: String) : WebTaskState()
}

class WebViewModel(private val container: AppContainer) : ViewModel() {

    val enabled: StateFlow<Boolean> = container.settings.settings
        .map { it.web.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val feeds: StateFlow<List<SavedFeed>> = container.settings.settings
        .map { it.web.feeds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val novels: StateFlow<List<WebNovel>> = container.settings.settings
        .map { it.web.novels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _task = kotlinx.coroutines.flow.MutableStateFlow<WebTaskState>(WebTaskState.Idle)
    val task: StateFlow<WebTaskState> = _task

    private val _feedItems = kotlinx.coroutines.flow.MutableStateFlow<List<FeedItem>>(emptyList())
    val feedItems: StateFlow<List<FeedItem>> = _feedItems

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { container.settings.update { it.copy(web = it.web.copy(enabled = value)) } }
    }

    fun savePage(url: String) = runTask { container.web.savePage(url) }

    fun addFeed(url: String) {
        viewModelScope.launch {
            _task.value = WebTaskState.Working
            try {
                val feedUrl = container.web.discoverFeed(url) ?: url
                val parsed = container.web.feedItems(feedUrl)
                container.settings.update {
                    it.copy(
                        web = it.web.copy(
                            feeds = it.web.feeds + app.folio.data.settings.SavedFeed(feedUrl, parsed.title ?: feedUrl, parsed.siteUrl),
                        ),
                    )
                }
                _task.value = WebTaskState.Idle
            } catch (e: Exception) {
                _task.value = WebTaskState.Failed(e.message ?: "Couldn't add that feed")
            }
        }
    }

    fun openFeed(feed: SavedFeed) {
        viewModelScope.launch {
            _task.value = WebTaskState.Working
            try {
                _feedItems.value = container.web.feedItems(feed.url).items
                _task.value = WebTaskState.Idle
            } catch (e: Exception) {
                _task.value = WebTaskState.Failed(e.message ?: "Couldn't load that feed")
            }
        }
    }

    fun saveFeedItem(item: FeedItem) = runTask { container.web.saveFeedItem(item) }

    fun removeFeed(feed: SavedFeed) {
        viewModelScope.launch {
            container.settings.update { it.copy(web = it.web.copy(feeds = it.web.feeds - feed)) }
        }
    }

    fun startWebNovel(url: String, title: String) = runTask {
        val bookId = container.web.saveWebNovel(url, title)
        container.settings.update {
            it.copy(
                web = it.web.copy(
                    novels = it.web.novels + WebNovel(bookId, title, lastUrl = url, nextUrl = url, chapters = 11),
                ),
            )
        }
        bookId
    }

    fun checkForNewChapters(novel: WebNovel) {
        viewModelScope.launch {
            _task.value = WebTaskState.Working
            try {
                val updated = container.web.checkForNewChapters(novel)
                container.settings.update {
                    it.copy(web = it.web.copy(novels = it.web.novels.map { n -> if (n.bookId == novel.bookId) updated else n }))
                }
                _task.value = WebTaskState.Done(novel.bookId)
            } catch (e: Exception) {
                _task.value = WebTaskState.Failed(e.message ?: "Couldn't check for new chapters")
            }
        }
    }

    fun clearTask() {
        _task.value = WebTaskState.Idle
    }

    private fun runTask(block: suspend () -> Long) {
        viewModelScope.launch {
            _task.value = WebTaskState.Working
            _task.value = try {
                WebTaskState.Done(block())
            } catch (e: Exception) {
                WebTaskState.Failed(e.message ?: "Something went wrong")
            }
        }
    }
}
