package app.folio.ui.screens.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.R
import app.folio.data.settings.SavedFeed
import app.folio.data.settings.WebNovel
import app.folio.web.FeedItem
import app.folio.web.NothingToReadException
import app.folio.web.WebReadingOffException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the screen is doing right now, shown as a line with a progress indicator. */
data class WebBusy(val label: Int, val count: Int = 0)

/** A message for the snackbar: a string resource, with the site's own words when there are some. */
data class WebMessage(val text: Int, val detail: String? = null)

class WebViewModel(private val container: AppContainer) : ViewModel() {

    private val web = container.web

    val enabled: StateFlow<Boolean> = container.settings.settings.map { it.web.enabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val feeds: StateFlow<List<SavedFeed>> = container.settings.settings.map { it.web.feeds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val novels: StateFlow<List<WebNovel>> = container.settings.settings.map { it.web.novels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow<WebBusy?>(null)
    val busy: StateFlow<WebBusy?> = _busy.asStateFlow()

    private val _openFeed = MutableStateFlow<SavedFeed?>(null)
    val openFeed: StateFlow<SavedFeed?> = _openFeed.asStateFlow()

    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items.asStateFlow()

    private val _saved = MutableStateFlow<Map<String, Long>>(emptyMap())
    val saved: StateFlow<Map<String, Long>> = _saved.asStateFlow()

    private val _messages = MutableSharedFlow<WebMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<WebMessage> = _messages.asSharedFlow()

    private val _openBook = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openBook: SharedFlow<Long> = _openBook.asSharedFlow()

    /** A shared web address, taken once. */
    fun takeSharedUrl(): String? = container.pendingWebUrl.value?.also { container.pendingWebUrl.value = null }

    fun enable() {
        viewModelScope.launch { container.settings.update { it.copy(web = it.web.copy(enabled = true)) } }
    }

    fun savePage(url: String) = work(WebBusy(R.string.web_busy_saving)) {
        val bookId = web.savePage(url)
        _openBook.emit(bookId)
    }

    fun addFeed(url: String) = work(WebBusy(R.string.web_busy_feed)) {
        val feed = web.addFeed(url)
        _messages.emit(WebMessage(R.string.web_feed_added, feed.title))
    }

    fun removeFeed(feed: SavedFeed) {
        viewModelScope.launch { web.removeFeed(feed) }
    }

    fun showFeed(feed: SavedFeed?) {
        _openFeed.value = feed
        _items.value = emptyList()
        if (feed == null) return
        viewModelScope.launch {
            _items.value = web.cachedItems(feed.url)
            _saved.value = web.savedItems()
        }
        refreshFeed()
    }

    fun refreshFeed() {
        val feed = _openFeed.value ?: return
        work(WebBusy(R.string.web_busy_refreshing)) {
            val fresh = web.refreshFeed(feed)
            if (_openFeed.value?.url == feed.url) _items.value = fresh
        }
    }

    fun openItem(item: FeedItem) {
        _saved.value[item.link]?.let { bookId ->
            viewModelScope.launch {
                // Saved before, and still in the library: open it rather than saving it again.
                if (container.library.entity(bookId) != null) {
                    _openBook.emit(bookId)
                    return@launch
                }
                saveItem(item)
            }
            return
        }
        saveItem(item)
    }

    private fun saveItem(item: FeedItem) = work(WebBusy(R.string.web_busy_saving)) {
        val bookId = web.saveItem(item)
        _saved.value = web.savedItems()
        _openBook.emit(bookId)
    }

    fun startNovel(url: String) = work(WebBusy(R.string.web_busy_chapters)) {
        val bookId = web.startNovel(url, ahead = 10) { count -> _busy.value = WebBusy(R.string.web_busy_chapters, count) }
        _openBook.emit(bookId)
    }

    fun updateNovel(novel: WebNovel) = work(WebBusy(R.string.web_busy_chapters)) {
        val added = web.updateNovel(novel) { count -> _busy.value = WebBusy(R.string.web_busy_chapters, count) }
        _messages.emit(
            if (added == 0) WebMessage(R.string.web_novel_no_new, novel.title)
            else WebMessage(R.string.web_novel_added, "$added"),
        )
    }

    fun forgetNovel(novel: WebNovel) {
        viewModelScope.launch { web.forgetNovel(novel) }
    }

    fun openNovel(novel: WebNovel) {
        viewModelScope.launch {
            if (container.library.entity(novel.bookId) != null) {
                _openBook.emit(novel.bookId)
            } else {
                _messages.emit(WebMessage(R.string.web_error_book_gone))
            }
        }
    }

    private fun work(busy: WebBusy, block: suspend () -> Unit) {
        if (_busy.value != null) return
        _busy.value = busy
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit(e.toMessage())
            } finally {
                _busy.value = null
            }
        }
    }

    private fun Exception.toMessage(): WebMessage = when (this) {
        is WebReadingOffException -> WebMessage(R.string.web_off_title)
        is NothingToReadException -> WebMessage(
            when (kind) {
                NothingToReadException.Kind.NO_TEXT -> R.string.web_error_no_text
                NothingToReadException.Kind.NO_FEED -> R.string.web_error_no_feed
                NothingToReadException.Kind.FEED_UNREADABLE -> R.string.web_error_feed_unreadable
                NothingToReadException.Kind.NO_CHAPTER -> R.string.web_error_no_chapter
                NothingToReadException.Kind.BOOK_GONE -> R.string.web_error_book_gone
                NothingToReadException.Kind.BOOK_LOCKED -> R.string.web_error_book_locked
            },
        )
        is IllegalArgumentException -> WebMessage(R.string.web_error_address)
        else -> WebMessage(R.string.web_error_network, message)
    }
}
