package app.folio.importer

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.folio.FolioApp
import app.folio.core.model.IndexState
import app.folio.data.db.BookEntity
import app.folio.data.db.BookTextFts
import app.folio.reader.api.BookSource

/**
 * Builds the full-text index in the background, a few books at a time, so searching inside books
 * is instant later and importing stays fast now. Interrupted work resumes because each book's
 * index state is recorded in the database.
 */
class IndexWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? FolioApp)?.container ?: return Result.success()
        if (!container.settings.current().storage.fullTextIndexing) return Result.success()

        val library = container.library
        val searchDao = container.database.search()

        while (!isStopped) {
            val batch = library.pendingIndex(BATCH_SIZE)
            if (batch.isEmpty()) break
            batch.forEach { book ->
                if (isStopped) return Result.success()
                indexBook(book, searchDao, container)
            }
        }
        return Result.success()
    }

    private suspend fun indexBook(
        book: BookEntity,
        searchDao: app.folio.data.db.SearchDao,
        container: app.folio.AppContainer,
    ) {
        val extractor = container.readers.textExtractor(book.format)
        if (extractor == null) {
            container.library.setIndexState(book.id, IndexState.UNSUPPORTED)
            return
        }
        if (!container.files.exists(book.uri)) {
            container.library.setIndexState(book.id, IndexState.FAILED)
            return
        }

        container.library.setIndexState(book.id, IndexState.INDEXING)
        searchDao.deleteForBook(book.id)

        val buffer = mutableListOf<BookTextFts>()
        var indexed = 0
        try {
            extractor.extract(BookSource(book.id, book.uri, book.format, book.fileName)) { chunk ->
                buffer += BookTextFts(
                    bookId = book.id,
                    location = app.folio.data.db.LocationCodec.encode(chunk.location),
                    label = chunk.label,
                    text = chunk.text,
                )
                indexed++
                if (buffer.size >= FLUSH_EVERY) {
                    searchDao.insertChunks(buffer.toList())
                    buffer.clear()
                }
            }
            if (buffer.isNotEmpty()) searchDao.insertChunks(buffer.toList())
            container.library.setIndexState(
                book.id,
                if (indexed == 0) IndexState.UNSUPPORTED else IndexState.DONE,
            )
        } catch (e: Exception) {
            container.library.setIndexState(book.id, IndexState.FAILED)
        }
    }

    companion object {
        private const val BATCH_SIZE = 4
        private const val FLUSH_EVERY = 40
        const val WORK_NAME = "folio-index"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<IndexWorker>()
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build(),
            )
        }

        fun reschedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<IndexWorker>().build(),
            )
        }
    }
}
