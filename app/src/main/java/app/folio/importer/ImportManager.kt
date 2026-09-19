package app.folio.importer

import android.net.Uri
import app.folio.core.model.BookFormat
import app.folio.core.model.IndexState
import app.folio.core.model.StorageMode
import app.folio.data.db.BookEntity
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.files.Hashing
import app.folio.data.repo.LibraryRepository
import app.folio.data.repo.NewBook
import app.folio.data.settings.BookReaderPrefs
import app.folio.data.settings.ComicDirection
import app.folio.data.settings.ImportMode
import app.folio.data.settings.SettingsRepository
import app.folio.reader.ReaderRegistry
import app.folio.reader.api.BookSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

data class ImportProgress(
    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val currentName: String? = null,
) {
    val fraction: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

/** A file waiting on the user's answer to "this book may already exist". */
data class PendingDuplicate(
    val token: Long,
    val uri: String,
    val fileName: String,
    val existingBookId: Long,
    val existingTitle: String,
)

sealed interface ImportEvent {
    data class Imported(val bookId: Long, val title: String) : ImportEvent
    data class Failed(val fileName: String, val reason: ImportFailure) : ImportEvent
    data class Duplicate(val pending: PendingDuplicate) : ImportEvent
    data class Finished(val imported: Int, val failed: Int, val skipped: Int) : ImportEvent
}

enum class ImportFailure { UNSUPPORTED, UNREADABLE, CORRUPTED, EMPTY, PERMISSION }

enum class DuplicateAction { OPEN_EXISTING, IMPORT_SEPARATELY, REPLACE, CANCEL }

/**
 * Imports run in the background off a queue, so adding a folder of hundreds of books never blocks
 * the UI. Each file is validated, identified, has its metadata and cover extracted, and is then
 * inserted with its original file left exactly where it was.
 */
class ImportManager(
    private val library: LibraryRepository,
    private val files: FileAccess,
    private val cache: CacheStore,
    private val registry: ReaderRegistry,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private data class Job(
        val uri: String,
        val forceCopy: Boolean,
        val skipDuplicateCheck: Boolean = false,
        val replaceBookId: Long? = null,
    )

    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val tokens = AtomicLong(1)
    private val pending = mutableMapOf<Long, Job>()

    private val _progress = MutableStateFlow(ImportProgress())
    val progress: StateFlow<ImportProgress> = _progress.asStateFlow()

    private val _events = MutableSharedFlow<ImportEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<ImportEvent> = _events.asSharedFlow()

    private var worker: kotlinx.coroutines.Job? = null
    private var imported = 0
    private var failed = 0
    private var skipped = 0

    fun enqueue(uris: List<String>, forceCopy: Boolean = false) {
        if (uris.isEmpty()) return
        _progress.value = _progress.value.copy(
            running = true,
            total = _progress.value.total + uris.size,
        )
        uris.forEach { uri -> queue.trySend(Job(uri, forceCopy)) }
        ensureWorker()
    }

    /** Import every supported file inside a folder the user picked. */
    fun enqueueFolder(treeUri: String) {
        scope.launch {
            val uri = Uri.parse(treeUri)
            files.takePersistablePermission(uri)
            val found = files.scanFolder(uri)
            if (found.isEmpty()) {
                _events.emit(ImportEvent.Finished(0, 0, 0))
                return@launch
            }
            enqueue(found.map { it.uri })
        }
    }

    fun resolveDuplicate(token: Long, action: DuplicateAction) {
        val job = pending.remove(token) ?: return
        when (action) {
            DuplicateAction.IMPORT_SEPARATELY -> {
                _progress.value = _progress.value.copy(running = true, total = _progress.value.total + 1)
                queue.trySend(job.copy(skipDuplicateCheck = true, replaceBookId = null))
                ensureWorker()
            }
            DuplicateAction.REPLACE -> {
                _progress.value = _progress.value.copy(running = true, total = _progress.value.total + 1)
                queue.trySend(job.copy(skipDuplicateCheck = true))
                ensureWorker()
            }
            DuplicateAction.OPEN_EXISTING, DuplicateAction.CANCEL -> skipped++
        }
    }

    private fun ensureWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (true) {
                val job = queue.tryReceive().getOrNull() ?: break
                _progress.value = _progress.value.copy(
                    currentName = files.info(job.uri)?.name,
                )
                runCatching { process(job) }
                _progress.value = _progress.value.copy(completed = _progress.value.completed + 1)
            }
            _events.emit(ImportEvent.Finished(imported, failed, skipped))
            _progress.value = ImportProgress()
            imported = 0
            failed = 0
            skipped = 0
        }
    }

    private suspend fun process(job: Job) {
        val info = files.info(job.uri) ?: run {
            failed++
            _events.emit(ImportEvent.Failed(job.uri.substringAfterLast('/'), ImportFailure.UNREADABLE))
            return
        }

        val format = FormatDetector.detect(files, job.uri, info.name, info.mimeType, info.isDirectory)
        if (format == null || !registry.supports(format)) {
            failed++
            _events.emit(ImportEvent.Failed(info.name, ImportFailure.UNSUPPORTED))
            return
        }

        val appSettings = settings.current()
        val shouldCopy = job.forceCopy ||
            appSettings.storage.importMode == ImportMode.COPY ||
            (!info.isDirectory && !holdsPermission(job.uri))

        var workingUri = job.uri
        var storage = StorageMode.LINKED
        if (shouldCopy && !info.isDirectory) {
            val copied = files.copyIntoAppStorage(job.uri, cache.importedDir, info.name)
            if (copied == null) {
                failed++
                _events.emit(ImportEvent.Failed(info.name, ImportFailure.PERMISSION))
                return
            }
            workingUri = Uri.fromFile(copied).toString()
            storage = StorageMode.COPIED
        } else if (!info.isDirectory) {
            files.takePersistablePermission(Uri.parse(job.uri))
        }

        val size = files.info(workingUri)?.size ?: info.size
        val hash = if (info.isDirectory) {
            Hashing.sha256("${info.name}:${info.size}:${info.uri}")
        } else {
            files.openInput(workingUri)?.use { Hashing.quickHash(it, size) } ?: run {
                failed++
                _events.emit(ImportEvent.Failed(info.name, ImportFailure.UNREADABLE))
                return
            }
        }

        if (!job.skipDuplicateCheck) {
            val existing = library.findDuplicate(hash, size)
            if (existing != null) {
                val token = tokens.getAndIncrement()
                pending[token] = job.copy(replaceBookId = existing.id)
                _events.emit(
                    ImportEvent.Duplicate(
                        PendingDuplicate(token, job.uri, info.name, existing.id, existing.displayTitle),
                    ),
                )
                return
            }
        }

        val extractor = registry.metadataExtractor(format)
        val probeSource = BookSource(bookId = 0, uri = workingUri, format = format, fileName = info.name)
        val extracted = extractor?.extract(probeSource)?.getOrNull()

        if (extracted == null && extractor != null) {
            failed++
            _events.emit(ImportEvent.Failed(info.name, ImportFailure.CORRUPTED))
            return
        }

        val newBook = NewBook(
            uri = workingUri,
            fileName = info.name,
            fileSize = size,
            fileHash = hash,
            mimeType = info.mimeType,
            format = format,
            storage = storage,
            imported = extracted?.metadata ?: app.folio.core.model.BookMetadata(),
            pageCount = extracted?.pageCount,
            passwordProtected = extracted?.passwordProtected == true,
            indexState = if (registry.textExtractor(format) == null) IndexState.UNSUPPORTED else IndexState.PENDING,
        )

        val replaceId = job.replaceBookId
        val bookId = if (replaceId != null) {
            // "Replace" keeps the existing book's tags, notes and progress, and swaps the file.
            library.replaceFile(replaceId, newBook)
            replaceId
        } else {
            library.insert(newBook)
        }

        extracted?.cover?.let { cover ->
            library.setImportedCover(bookId, cover)
        }

        // A manga that says it reads right-to-left should open that way the first time.
        if (extracted?.rightToLeft == true) {
            library.setReaderPrefs(
                bookId,
                BookReaderPrefs.encode(BookReaderPrefs(comicDirection = ComicDirection.RTL)),
            )
        }

        imported++
        _events.emit(ImportEvent.Imported(bookId, newBook.imported.title ?: info.name))
    }

    private fun holdsPermission(uri: String): Boolean =
        files.hasPersistedPermission(uri) || Uri.parse(uri).scheme == "file"
}

/** Re-reads a file's metadata without touching anything the user has edited. */
class MetadataRescanner(
    private val library: LibraryRepository,
    private val registry: ReaderRegistry,
) {
    suspend fun rescan(book: BookEntity): Boolean {
        val extractor = registry.metadataExtractor(book.format) ?: return false
        val extracted = extractor.extract(
            BookSource(book.id, book.uri, book.format, book.fileName),
        ).getOrNull() ?: return false
        library.applyRescan(book.id, extracted.metadata, extracted.pageCount)
        extracted.cover?.let { library.setImportedCover(book.id, it) }
        return true
    }
}

internal fun BookFormat.isComicFolder(): Boolean = this == BookFormat.IMAGE_FOLDER
