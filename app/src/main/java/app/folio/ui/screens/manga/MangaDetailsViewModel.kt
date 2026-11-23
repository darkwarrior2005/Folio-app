package app.folio.ui.screens.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.ChapterCondition
import app.folio.core.model.ChapterFacts
import app.folio.core.model.ChapterSelection
import app.folio.core.model.MangaDownloadStatus
import app.folio.core.model.NoChaptersException
import app.folio.data.db.MangaChapterEntity
import app.folio.data.db.MangaDownloadEntity
import app.folio.data.db.MangaEntity
import app.folio.manga.source.needsWebView
import app.folio.manga.source.sourceErrorMessage
import app.folio.manga.toSChapter
import app.folio.manga.toSManga
import eu.kanade.tachiyomi.source.ConfigurableSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A chapter with the download state the row needs. */
data class ChapterRow(
    val chapter: MangaChapterEntity,
    val download: MangaDownloadEntity?,
) {
    val downloaded: Boolean get() = download?.status == MangaDownloadStatus.DOWNLOADED
    val downloading: Boolean
        get() = download?.status == MangaDownloadStatus.QUEUED ||
            download?.status == MangaDownloadStatus.DOWNLOADING

    fun facts() = ChapterFacts(
        id = chapter.id,
        read = chapter.read,
        downloaded = downloaded,
        queued = downloading,
        bookmark = chapter.bookmark,
        number = chapter.chapterNumber,
    )
}

/** Why the last refresh failed, kept on screen until the next one starts. */
data class RefreshError(
    val message: String,
    val needsWebView: Boolean,
    /** The source listed no chapters at all, as opposed to failing. */
    val noChapters: Boolean = false,
    /** The source has settings of its own that may change what it lists. */
    val sourceHasSettings: Boolean = false,
)

class MangaDetailsViewModel(
    private val container: AppContainer,
    private val mangaId: Long,
) : ViewModel() {

    val manga: StateFlow<MangaEntity?> = container.manga.observe(mangaId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chapters: StateFlow<List<ChapterRow>> = combine(
        container.manga.observeChapters(mangaId),
        container.downloads.observeDownloadsFor(mangaId),
    ) { chapters, downloads ->
        val byChapter = downloads.associateBy { it.chapterId }
        chapters.map { ChapterRow(it, byChapter[it.id]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _refreshError = MutableStateFlow<RefreshError?>(null)
    val refreshError: StateFlow<RefreshError?> = _refreshError.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())

    /** Chapters picked for a bulk action; empty when not selecting. */
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    /** The chapter a range selection extends from: the last one picked. */
    private var anchorId: Long? = null

    init {
        // So a chapter's row agrees with what opening it will actually find on disk.
        viewModelScope.launch { container.downloads.healMissingFiles() }

        // Details and chapters are fetched the first time a manga is opened. Some sources mark
        // search results as already initialized, so the chapter list having never been fetched
        // counts too; otherwise such a manga would open with no chapters.
        viewModelScope.launch {
            val current = container.manga.get(mangaId)
            if (current != null && (!current.initialized || current.lastChapterFetchAt == null)) refresh()
        }
    }

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        _refreshError.value = null
        viewModelScope.launch {
            try {
                container.mangaAccess.require()
                val current = container.manga.get(mangaId) ?: return@launch
                val source = container.sources.get(current.sourceId)
                    ?: error(container.sources.unavailableMessage(current.sourceId))

                val chapters = container.manga.chapters(mangaId).map { it.toSChapter() }
                val update = withContext(Dispatchers.IO) {
                    source.getMangaUpdate(
                        manga = current.toSManga(),
                        chapters = chapters,
                        fetchDetails = true,
                        fetchChapters = true,
                    )
                }
                container.manga.applyDetails(mangaId, update.manga)
                val result = container.manga.syncChapters(mangaId, update.chapters, source)
                if (result.added > 0) {
                    _message.value = "${result.added} new chapters"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: NoChaptersException) {
                val sourceId = container.manga.get(mangaId)?.sourceId
                _refreshError.value = RefreshError(
                    message = e.sourceErrorMessage(),
                    needsWebView = false,
                    noChapters = true,
                    sourceHasSettings = sourceId?.let { container.sources.get(it) } is ConfigurableSource,
                )
            } catch (e: Exception) {
                _refreshError.value = RefreshError(e.sourceErrorMessage(), e.needsWebView())
            } catch (e: LinkageError) {
                _refreshError.value = RefreshError(e.sourceErrorMessage(), needsWebView = false)
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val current = container.manga.get(mangaId) ?: return@launch
            container.manga.setFavorite(mangaId, !current.favorite)
        }
    }

    fun download(chapterIds: List<Long>) {
        if (chapterIds.isEmpty()) return
        viewModelScope.launch {
            try {
                container.downloads.download(mangaId, chapterIds)
            } catch (e: Exception) {
                _message.value = e.message ?: "The download could not be queued"
            }
        }
    }

    fun deleteDownloads(chapterIds: List<Long>) {
        viewModelScope.launch { container.downloads.delete(chapterIds) }
    }

    fun setRead(chapterIds: List<Long>, read: Boolean) {
        viewModelScope.launch { container.manga.setChaptersRead(chapterIds, read) }
    }

    fun toggleBookmark(chapter: MangaChapterEntity) {
        viewModelScope.launch { container.manga.setChapterBookmark(chapter.id, !chapter.bookmark) }
    }

    private fun facts(): List<ChapterFacts> = chapters.value.map { it.facts() }

    fun toggleSelection(chapterId: Long) {
        _selection.update { if (chapterId in it) it - chapterId else it + chapterId }
        anchorId = chapterId
    }

    /** Adds every chapter between the last one picked and [chapterId]. */
    fun selectRangeTo(chapterId: Long) {
        val from = anchorId ?: chapterId
        _selection.update { it + ChapterSelection.between(facts(), from, chapterId) }
        anchorId = chapterId
    }

    fun selectAll() {
        _selection.value = chapters.value.mapTo(mutableSetOf()) { it.chapter.id }
    }

    fun invertSelection() {
        _selection.value = ChapterSelection.inverted(facts(), _selection.value)
    }

    fun selectWhere(condition: ChapterCondition) {
        _selection.value = ChapterSelection.matching(facts(), condition)
        anchorId = null
    }

    fun selectNumbered(from: Float, to: Float) {
        _selection.value = ChapterSelection.numbered(facts(), from, to)
        anchorId = null
    }

    fun clearSelection() {
        _selection.value = emptySet()
        anchorId = null
    }

    fun downloadSelected() {
        queueDownloads(ChapterSelection.toDownload(facts(), _selection.value))
        clearSelection()
    }

    fun deleteSelectedDownloads() {
        deleteDownloads(_selection.value.toList())
        clearSelection()
    }

    fun markSelectedRead(read: Boolean) {
        setRead(_selection.value.toList(), read)
        clearSelection()
    }

    fun bookmarkSelected(bookmark: Boolean) {
        val ids = _selection.value
        viewModelScope.launch { ids.forEach { container.manga.setChapterBookmark(it, bookmark) } }
        clearSelection()
    }

    /** The next [count] unread chapters after the last one read, skipping what is already there. */
    fun downloadNextUnread(count: Int) {
        queueDownloads(ChapterSelection.nextUnreadToDownload(facts(), count))
    }

    fun downloadWhere(condition: ChapterCondition) {
        val chapters = facts()
        queueDownloads(ChapterSelection.toDownload(chapters, ChapterSelection.matching(chapters, condition)))
    }

    fun downloadAll() {
        val chapters = facts()
        queueDownloads(ChapterSelection.toDownload(chapters, chapters.mapTo(mutableSetOf()) { it.id }))
    }

    fun downloadNumbered(from: Float, to: Float) {
        val chapters = facts()
        queueDownloads(ChapterSelection.toDownload(chapters, ChapterSelection.numbered(chapters, from, to)))
    }

    /** Queues [chapterIds], saying how many, or that there was nothing left to fetch. */
    private fun queueDownloads(chapterIds: List<Long>) {
        if (chapterIds.isEmpty()) {
            _message.value = "Nothing to download: those chapters are already downloaded or queued"
            return
        }
        download(chapterIds)
        _message.value = if (chapterIds.size == 1) "Downloading 1 chapter" else "Downloading ${chapterIds.size} chapters"
    }

    /** The chapter the Read button opens: the earliest unread, else the first. */
    fun nextChapterId(): Long? {
        val rows = chapters.value
        return rows.lastOrNull { !it.chapter.read }?.chapter?.id ?: rows.lastOrNull()?.chapter?.id
    }

    fun clearMessage() {
        _message.value = null
    }
}
