package app.folio.ui.screens.reader

import app.folio.reader.api.ZoomNotice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.BookLocation
import app.folio.core.model.HighlightColor
import app.folio.core.model.InBookSearchHit
import app.folio.core.model.InkPoint
import app.folio.core.model.InkStroke
import app.folio.core.model.InkTool
import app.folio.core.model.InkToolStyle
import app.folio.core.model.ReadingStatus
import app.folio.core.model.TocEntry
import app.folio.data.db.BookmarkEntity
import app.folio.data.db.DrawnNoteEntity
import app.folio.data.db.HighlightEntity
import app.folio.data.db.LocationCodec
import app.folio.data.db.toInkStroke
import app.folio.data.settings.AppSettings
import app.folio.data.settings.BookReaderPrefs
import app.folio.pomodoro.PomodoroState
import app.folio.reader.api.BookOpenError
import app.folio.reader.api.BookSource
import app.folio.reader.api.ReaderController
import app.folio.reader.api.ReaderEngine
import app.folio.reader.api.ReaderHost
import app.folio.reader.api.ReaderSelection
import app.folio.reader.api.ScribbleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(val host: ReaderHost, val engine: ReaderEngine, val title: String) : ReaderUiState
    data class Failed(val error: BookOpenError) : ReaderUiState
}

data class ReaderPosition(
    val location: BookLocation? = null,
    val progress: Float = 0f,
    val page: Int? = null,
    val pageCount: Int? = null,
    val label: String = "",
)

@OptIn(FlowPreview::class)
class ReaderViewModel(
    private val container: AppContainer,
    private val bookId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val _position = MutableStateFlow(ReaderPosition())
    val position: StateFlow<ReaderPosition> = _position.asStateFlow()

    private val _toc = MutableStateFlow<List<TocEntry>>(emptyList())
    val toc: StateFlow<List<TocEntry>> = _toc.asStateFlow()

    private val _selection = MutableStateFlow<ReaderSelection?>(null)
    val selection: StateFlow<ReaderSelection?> = _selection.asStateFlow()

    private val _controlsVisible = MutableStateFlow(true)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _passwordRequired = MutableStateFlow(false)
    val passwordRequired: StateFlow<Boolean> = _passwordRequired.asStateFlow()

    private val _password = MutableStateFlow<String?>(null)

    private val _searchResults = MutableStateFlow<List<InBookSearchHit>>(emptyList())
    val searchResults: StateFlow<List<InBookSearchHit>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _focusMode = MutableStateFlow(false)
    val focusMode: StateFlow<Boolean> = _focusMode.asStateFlow()

    private val _finishedPrompt = MutableStateFlow(false)
    val finishedPrompt: StateFlow<Boolean> = _finishedPrompt.asStateFlow()

    private val _bookPrefs = MutableStateFlow(BookReaderPrefs())
    val bookPrefsState: StateFlow<BookReaderPrefs> get() = _bookPrefs.asStateFlow()

    private val _zoomNotices = kotlinx.coroutines.flow.MutableSharedFlow<ZoomNotice>(extraBufferCapacity = 4)
    val zoomNotices: SharedFlow<ZoomNotice> = _zoomNotices.asSharedFlow()

    private val _zoomLabel = MutableStateFlow<String?>(null)
    val zoomLabel: StateFlow<String?> = _zoomLabel.asStateFlow()

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val highlights: StateFlow<List<HighlightEntity>> = container.annotations.highlights(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val bookmarks: StateFlow<List<BookmarkEntity>> = container.annotations.bookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pomodoro: StateFlow<PomodoroState> = container.pomodoro.state
    val pomodoroRemaining: StateFlow<Long> = container.pomodoro.remaining

    private val _scribbleActive = MutableStateFlow(false)

    val scribble: StateFlow<ScribbleState> = combine(_scribbleActive, settings) { active, current ->
        val tool = current.scribble.lastTool
        ScribbleState(active, tool, current.scribble.styleFor(tool))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ScribbleState())

    val pageInk: StateFlow<Map<Int, List<InkStroke>>> = container.ink.pageStrokes(bookId)
        .map { strokes -> strokes.filter { it.page != null }.groupBy({ it.page!! }, { it.toInkStroke() }) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val drawnNotes: StateFlow<List<DrawnNoteEntity>> = container.ink.drawnNotes(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // App scope: a stroke finished right before leaving the reader must still be saved.
    private val inkSession = InkSession(container.ink, container.appScope)
    val inkUndoRedo: StateFlow<UndoRedo> = inkSession.undoRedo

    private val _openDrawnNote = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openDrawnNote: SharedFlow<Long> = _openDrawnNote.asSharedFlow()

    // Guards startScribble(inkOnPages = false): only touched on the main thread, so a quick
    // double tap that fires the tap handler twice before the first coroutine resumes still
    // creates just one drawn note.
    private var creatingDrawnNote = false

    private val jumps = MutableSharedFlow<BookLocation>(extraBufferCapacity = 8)
    private val pendingSave = MutableStateFlow<ReaderPosition?>(null)

    private var controller: ReaderController? = null
    private var initialLocation: BookLocation? = null
    private var sessionStartedAt: Long = 0
    private var sessionStartProgress: Float = 0f
    private var sessionStartPage: Int? = null
    private var promptedFinished = false

    val controllerState = MutableStateFlow<ReaderController?>(null)

    init {
        viewModelScope.launch {
            val entity = container.library.entity(bookId)
            if (entity == null) {
                _uiState.value = ReaderUiState.Failed(BookOpenError.FileMissing)
                return@launch
            }
            if (!container.files.exists(entity.uri)) {
                container.library.isAvailable(bookId)
                _uiState.value = ReaderUiState.Failed(BookOpenError.FileMissing)
                return@launch
            }
            val engine = container.readers.engineFor(entity.format)
            if (engine == null) {
                _uiState.value = ReaderUiState.Failed(BookOpenError.Unsupported)
                return@launch
            }

            initialLocation = container.library.position(bookId)
            _bookPrefs.value = BookReaderPrefs.decode(entity.readerPrefs)
            _position.value = ReaderPosition(
                location = initialLocation,
                progress = entity.progress,
                page = initialLocation?.page,
                pageCount = entity.pageCount,
                label = initialLocation?.label.orEmpty(),
            )
            _focusMode.value = container.settings.current().behavior.focusModeByDefault

            container.library.recordOpen(bookId)
            sessionStartedAt = System.currentTimeMillis()
            sessionStartProgress = entity.progress
            sessionStartPage = initialLocation?.page
            container.pomodoro.attachBook(bookId)
            container.bookMusic.onBookOpened(bookId, entity.format.family)

            _uiState.value = ReaderUiState.Ready(
                host = Host(BookSource(bookId, entity.uri, entity.format, entity.fileName)),
                engine = engine,
                title = entity.displayTitle,
            )
        }

        // Writing on every scroll tick would hammer the database; a short debounce is plenty,
        // and leaving the reader flushes immediately.
        viewModelScope.launch {
            pendingSave.filterNotNull().debounce(SAVE_DEBOUNCE_MS).collect { position ->
                persist(position)
            }
        }
    }

    private inner class Host(override val source: BookSource) : ReaderHost {
        override val initialLocation: BookLocation? get() = this@ReaderViewModel.initialLocation
        override val settings: StateFlow<AppSettings> get() = this@ReaderViewModel.settings
        override val highlights: StateFlow<List<HighlightEntity>> get() = this@ReaderViewModel.highlights
        override val bookmarks: StateFlow<List<BookmarkEntity>> get() = this@ReaderViewModel.bookmarks
        override val jumps: SharedFlow<BookLocation> get() = this@ReaderViewModel.jumps.asSharedFlow()
        override val password: StateFlow<String?> get() = _password.asStateFlow()
        override val bookPrefs: StateFlow<BookReaderPrefs> get() = _bookPrefs.asStateFlow()

        override fun updateBookPrefs(transform: (BookReaderPrefs) -> BookReaderPrefs) {
            this@ReaderViewModel.updateBookPrefs(transform)
        }

        override fun onProgress(
            location: BookLocation,
            progress: Float,
            page: Int?,
            pageCount: Int?,
            label: String,
        ) {
            val next = ReaderPosition(location, progress, page, pageCount, label)
            _position.value = next
            pendingSave.value = next
            if (progress >= FINISH_PROMPT_AT && !promptedFinished) {
                promptedFinished = true
                _finishedPrompt.value = true
            }
        }

        override fun onToc(entries: List<TocEntry>) {
            _toc.value = entries
        }

        override fun onError(error: BookOpenError) {
            _uiState.value = ReaderUiState.Failed(error)
        }

        override fun onCenterTap() {
            _controlsVisible.value = !_controlsVisible.value
        }

        override fun onSelectionChanged(selection: ReaderSelection?) {
            _selection.value = selection
        }

        override fun onControllerReady(controller: ReaderController?) {
            this@ReaderViewModel.controller = controller
            controllerState.value = controller
        }

        override fun requestPassword() {
            _passwordRequired.value = true
        }

        override fun onZoomNotice(notice: ZoomNotice) {
            if (_bookPrefs.value.zoomLocked == true) return
            if (notice == ZoomNotice.ReflowsToTextSize) {
                if (_bookPrefs.value.reflowZoomNoticeShown == true) return
                updateBookPrefs { it.copy(reflowZoomNoticeShown = true) }
            }
            _zoomNotices.tryEmit(notice)
        }

        override fun onZoomLabel(label: String?) {
            _zoomLabel.value = label
        }

        override val scribble: StateFlow<ScribbleState> get() = this@ReaderViewModel.scribble
        override val pageInk: StateFlow<Map<Int, List<InkStroke>>> get() = this@ReaderViewModel.pageInk

        override fun onInkStroke(page: Int, points: List<InkPoint>, heightOverWidth: Float) {
            val current = scribble.value
            if (current.tool == InkTool.ERASER) return
            inkSession.addStroke(bookId, page, null, current.tool, current.style, points, heightOverWidth)
        }

        override fun onInkErase(page: Int, point: InkPoint, radius: Float, heightOverWidth: Float) {
            inkSession.erase(pageInk.value[page].orEmpty(), point, radius, heightOverWidth)
        }

        override fun onInkEraseEnd() = inkSession.endErase()
    }

    fun toggleZoomLock() {
        updateBookPrefs { it.copy(zoomLocked = !(it.zoomLocked ?: false)) }
    }

    fun moveZoomLockButton(landscape: Boolean, offset: app.folio.core.model.NormalizedOffset) {
        updateBookPrefs {
            if (landscape) it.copy(lockButtonLandscape = offset) else it.copy(lockButtonPortrait = offset)
        }
    }

    fun resetZoom() {
        controller?.resetZoom()
    }

    // ---- Scribble --------------------------------------------------------------

    /**
     * PDF and comics draw on the page. EPUB and text books get a drawn note at the current
     * position, opened full screen.
     */
    fun startScribble(inkOnPages: Boolean) {
        if (inkOnPages) {
            clearSelection()
            _scribbleActive.value = true
            _controlsVisible.value = false
            return
        }
        if (creatingDrawnNote) return
        creatingDrawnNote = true
        viewModelScope.launch {
            try {
                val current = _position.value
                val location = current.location ?: BookLocation(totalProgression = current.progress.toDouble())
                val label = current.label.ifBlank { location.label.orEmpty() }
                    .ifBlank { "${(current.progress * 100).toInt()}%" }
                val id = container.ink.createDrawnNote(bookId, location, label, current.progress)
                _openDrawnNote.emit(id)
            } finally {
                creatingDrawnNote = false
            }
        }
    }

    fun stopScribble() {
        _scribbleActive.value = false
        _controlsVisible.value = true
    }

    fun selectInkTool(tool: InkTool) {
        viewModelScope.launch {
            container.settings.update { it.copy(scribble = it.scribble.copy(lastTool = tool)) }
        }
    }

    fun updateInkStyle(style: InkToolStyle) {
        viewModelScope.launch {
            container.settings.update { it.copy(scribble = it.scribble.withStyle(it.scribble.lastTool, style)) }
        }
    }

    fun undoInk() = inkSession.undo()

    fun redoInk() = inkSession.redo()

    fun updateBookPrefs(transform: (BookReaderPrefs) -> BookReaderPrefs) {
        val updated = transform(_bookPrefs.value)
        _bookPrefs.value = updated
        viewModelScope.launch {
            container.library.setReaderPrefs(bookId, BookReaderPrefs.encode(updated))
        }
    }

    fun submitPassword(value: String) {
        _password.value = value
        _passwordRequired.value = false
    }

    fun setControlsVisible(visible: Boolean) {
        _controlsVisible.value = visible
    }

    fun toggleFocusMode() {
        _focusMode.value = !_focusMode.value
        if (_focusMode.value) _controlsVisible.value = false
    }

    fun goTo(location: BookLocation) {
        viewModelScope.launch { jumps.emit(location) }
    }

    fun nextPage() = controller?.nextPage()

    fun previousPage() = controller?.previousPage()

    fun goToPage(pageIndex: Int) {
        val pageCount = _position.value.pageCount ?: return
        val page = pageIndex.coerceIn(0, pageCount - 1)
        goTo(BookLocation(page = page, totalProgression = page.toDouble() / pageCount))
    }

    fun seekTo(progress: Float) {
        val pageCount = _position.value.pageCount
        val location = if (pageCount != null && pageCount > 0) {
            BookLocation(
                page = (progress * pageCount).toInt().coerceIn(0, pageCount - 1),
                totalProgression = progress.toDouble(),
            )
        } else {
            BookLocation(totalProgression = progress.toDouble())
        }
        goTo(location)
    }

    fun search(query: String) {
        viewModelScope.launch {
            _searching.value = true
            _searchResults.value = controller?.search(query).orEmpty()
            _searching.value = false
        }
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    // ---- Annotations -----------------------------------------------------------

    fun toggleBookmark() {
        viewModelScope.launch {
            val current = _position.value
            val location = current.location ?: return@launch
            val existing = container.annotations.bookmarkAt(bookId, current.page, current.progress)
            if (existing != null) {
                container.annotations.deleteBookmark(existing.id)
            } else {
                container.annotations.addBookmark(
                    bookId = bookId,
                    location = location,
                    positionLabel = current.label.ifBlank { location.label.orEmpty() },
                    progress = current.progress,
                )
            }
        }
    }

    fun addBookmarkWithNote(title: String?, note: String?) {
        viewModelScope.launch {
            val current = _position.value
            val location = current.location ?: return@launch
            container.annotations.addBookmark(
                bookId = bookId,
                location = location,
                positionLabel = current.label,
                progress = current.progress,
                title = title?.takeIf { it.isNotBlank() },
                note = note?.takeIf { it.isNotBlank() },
            )
        }
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch { container.annotations.deleteBookmark(id) }

    fun highlightSelection(color: HighlightColor, note: String? = null) {
        val selection = _selection.value ?: return
        viewModelScope.launch {
            val existingId = selection.existingHighlightId
            if (existingId != null) {
                container.annotations.setHighlightColor(existingId, color)
                if (note != null) container.annotations.setHighlightNote(existingId, note)
            } else {
                container.annotations.addHighlight(
                    bookId = bookId,
                    location = selection.location,
                    positionLabel = selection.positionLabel.ifBlank { _position.value.label },
                    progress = selection.progress,
                    text = selection.text,
                    color = color,
                    note = note,
                    page = selection.page,
                    rangeStart = selection.range?.start,
                    rangeEnd = selection.range?.end,
                )
            }
            clearSelection()
        }
    }

    fun deleteSelectedHighlight() {
        val id = _selection.value?.existingHighlightId ?: return
        viewModelScope.launch {
            container.annotations.deleteHighlight(id)
            clearSelection()
        }
    }

    fun noteForSelection(text: String) {
        val selection = _selection.value ?: return
        val existingId = selection.existingHighlightId
        if (existingId != null) {
            viewModelScope.launch {
                container.annotations.setHighlightNote(existingId, text)
                clearSelection()
            }
            return
        }
        if (selection.text.isNotBlank()) {
            // A note on selected text keeps the passage: it becomes a highlight carrying the note.
            highlightSelection(HighlightColor.YELLOW, note = text)
            return
        }
        viewModelScope.launch {
            container.annotations.addNote(
                bookId = bookId,
                location = selection.location,
                positionLabel = selection.positionLabel.ifBlank { _position.value.label },
                progress = selection.progress,
                text = text,
            )
            clearSelection()
        }
    }

    fun clearSelection() {
        _selection.value = null
        controller?.clearSelection()
    }

    fun locationOf(raw: String): BookLocation? = LocationCodec.decode(raw)

    // ---- Pomodoro --------------------------------------------------------------

    fun startPomodoro() = container.pomodoro.start(bookId = bookId)

    fun pausePomodoro() = container.pomodoro.pause()

    fun resumePomodoro() = container.pomodoro.resume()

    fun stopPomodoro() = container.pomodoro.stop()

    // ---- Persistence -----------------------------------------------------------

    private suspend fun persist(position: ReaderPosition) {
        val location = position.location ?: return
        container.library.saveProgress(
            bookId = bookId,
            location = location,
            progress = position.progress,
            page = position.page,
            pageCount = position.pageCount,
        )
    }

    /** Called when the reader leaves the foreground: never lose the place. */
    fun flush() {
        val position = pendingSave.value ?: _position.value
        viewModelScope.launch { persist(position) }
    }

    fun markFinished() {
        viewModelScope.launch { container.library.setStatus(listOf(bookId), ReadingStatus.FINISHED) }
        _finishedPrompt.value = false
    }

    fun dismissFinishedPrompt() {
        _finishedPrompt.value = false
    }

    /** Starts timing again when the reader comes back into view. */
    fun resumeSession() {
        if (sessionStartedAt > 0 || _uiState.value !is ReaderUiState.Ready) return
        val current = _position.value
        sessionStartedAt = System.currentTimeMillis()
        sessionStartProgress = current.progress
        sessionStartPage = current.page
    }

    fun endSession() {
        val current = _position.value
        val startedAt = sessionStartedAt
        if (startedAt <= 0) return
        sessionStartedAt = 0
        val pagesRead = ((current.page ?: 0) - (sessionStartPage ?: 0)).coerceAtLeast(0)
        // The app scope, not viewModelScope: this also runs from onCleared, when viewModelScope
        // has already been cancelled and the write would silently never happen.
        container.appScope.launch {
            persist(current)
            container.reading.recordSession(
                bookId = bookId,
                startedAt = startedAt,
                endedAt = System.currentTimeMillis(),
                pagesRead = pagesRead,
                startProgress = sessionStartProgress,
                endProgress = current.progress,
            )
        }
    }

    override fun onCleared() {
        endSession()
        super.onCleared()
    }

    companion object {
        private const val SAVE_DEBOUNCE_MS = 900L
        private const val FINISH_PROMPT_AT = 0.995f
    }
}
