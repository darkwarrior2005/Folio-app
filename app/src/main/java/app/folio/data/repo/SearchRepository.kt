package app.folio.data.repo

import app.folio.core.model.BookLocation
import app.folio.core.model.SearchQuery
import app.folio.data.db.FolioDatabase
import app.folio.data.db.LocationCodec

data class FullTextResult(
    val bookId: Long,
    val location: BookLocation?,
    val label: String,
    val snippet: String,
)

class SearchRepository(db: FolioDatabase) {

    private val dao = db.search()

    /** Search inside every indexed book. Returns nothing when the query has no usable words. */
    suspend fun searchEverywhere(query: String, limit: Int = 100): List<FullTextResult> {
        val match = SearchQuery.toFtsMatch(query) ?: return emptyList()
        return try {
            dao.search(match, limit).map { it.toResult() }
        } catch (e: Exception) {
            // A malformed MATCH expression should never crash the search screen.
            emptyList()
        }
    }

    suspend fun searchInBook(bookId: Long, query: String, limit: Int = 200): List<FullTextResult> {
        val match = SearchQuery.toFtsMatch(query) ?: return emptyList()
        return try {
            dao.searchInBook(match, bookId, limit).map { it.toResult() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun indexedBookCount(): Int = dao.indexedBookCount()

    suspend fun indexedBytes(): Long = dao.indexedChars() * 2

    suspend fun clearIndex() = dao.deleteAll()

    private fun app.folio.data.db.FtsHit.toResult() = FullTextResult(
        bookId = bookId,
        location = LocationCodec.decode(location),
        label = label,
        snippet = snippet,
    )
}
