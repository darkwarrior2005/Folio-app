package app.folio.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.data.backup.BackupSummary
import app.folio.data.backup.RestoreSummary
import app.folio.data.files.StorageUsage
import app.folio.data.settings.AppSettings
import app.folio.data.settings.PinHasher
import app.folio.importer.IndexWorker
import app.folio.pomodoro.ReminderWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SettingsMessage {
    data class BackupDone(val summary: BackupSummary) : SettingsMessage
    data class RestoreDone(val summary: RestoreSummary) : SettingsMessage
    data object Failed : SettingsMessage
    data class Exported(val count: Int) : SettingsMessage
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _storage = MutableStateFlow(StorageUsage(0, 0, 0, 0, 0))
    val storage: StateFlow<StorageUsage> = _storage.asStateFlow()

    private val _librarySize = MutableStateFlow(0L)
    val librarySize: StateFlow<Long> = _librarySize.asStateFlow()

    private val _indexedBooks = MutableStateFlow(0)
    val indexedBooks: StateFlow<Int> = _indexedBooks.asStateFlow()

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        refreshStorage()
    }

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch {
        container.settings.update(transform)
    }

    fun refreshStorage() = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            _storage.value = container.cache.sizes()
            _librarySize.value = container.library.librarySize()
            _indexedBooks.value = container.search.indexedBookCount()
        }
    }

    fun clearCache() = viewModelScope.launch {
        withContext(Dispatchers.IO) { container.cache.clearAllCaches() }
        refreshStorage()
    }

    fun clearTextIndex() = viewModelScope.launch {
        container.search.clearIndex()
        refreshStorage()
    }

    fun reindex(context: Context) = viewModelScope.launch {
        container.database.books().resetIndexStates()
        IndexWorker.reschedule(context)
    }

    fun scheduleReminder(context: Context) = viewModelScope.launch {
        val current = container.settings.current().notifications
        ReminderWorker.schedule(
            context = context,
            hour = current.reminderHour,
            minute = current.reminderMinute,
            enabled = current.dailyReminder || current.goalReminder || current.streakReminder,
        )
    }

    // ---- Backup ----------------------------------------------------------------

    fun backup(targetUri: String, includeFiles: Boolean) = viewModelScope.launch {
        _busy.value = true
        val result = container.backup.writeBackup(targetUri, includeFiles)
        _message.value = result.fold(
            onSuccess = { SettingsMessage.BackupDone(it) },
            onFailure = { SettingsMessage.Failed },
        )
        _busy.value = false
        refreshStorage()
    }

    fun restore(sourceUri: String) = viewModelScope.launch {
        _busy.value = true
        val result = container.backup.restore(sourceUri)
        _message.value = result.fold(
            onSuccess = { SettingsMessage.RestoreDone(it) },
            onFailure = { SettingsMessage.Failed },
        )
        _busy.value = false
        refreshStorage()
    }

    fun exportAnnotations(targetUri: String) = viewModelScope.launch {
        val result = container.backup.exportAnnotations(targetUri, null)
        _message.value = result.fold(
            onSuccess = { SettingsMessage.Exported(it) },
            onFailure = { SettingsMessage.Failed },
        )
    }

    fun clearMessage() {
        _message.value = null
    }

    // ---- Lock ------------------------------------------------------------------

    fun setPin(pin: String) = viewModelScope.launch {
        val salt = PinHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PinHasher.hash(pin, salt) }
        container.settings.update {
            it.copy(privacy = it.privacy.copy(pinHash = hash, pinSalt = salt))
        }
    }

    fun removePin() = viewModelScope.launch {
        container.settings.update {
            it.copy(privacy = it.privacy.copy(pinHash = null, pinSalt = null, biometricEnabled = false))
        }
    }

    fun resetAppData(onDone: () -> Unit) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val db = container.database
            db.search().deleteAll()
            db.reading().clearQueue()
            db.reading().deleteAllGoals()
            db.reading().deleteAllPomodoros()
            db.reading().deleteAllSessions()
            db.reading().deleteAllProgress()
            db.annotations().deleteAllNotes()
            db.annotations().deleteAllHighlights()
            db.annotations().deleteAllBookmarks()
            db.collections().deleteAll()
            db.tags().deleteAll()
            db.books().deleteAll()
            db.categories().deleteAll()
            container.cache.clearAllCaches()
        }
        container.settings.reset()
        onDone()
    }
}
