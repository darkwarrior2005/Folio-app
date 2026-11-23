package app.folio.core.model

import kotlin.random.Random

/** Small, friendly choices the home screen makes for the reader. */
object ComfortPicks {

    /** The book to pick up again: the one opened most recently that is started but not finished. */
    fun resume(books: List<LibraryBook>): LibraryBook? = books
        .filter { !it.missing && it.lastOpenedAt != null && it.isUnfinished() }
        .maxByOrNull { it.lastOpenedAt ?: 0L }

    /**
     * "Surprise me": a random book from the reading queue, or, with an empty queue, from the books
     * not finished yet, never the one being read right now. Null when there is nothing to offer.
     */
    fun surprise(books: List<LibraryBook>, queueIds: List<Long>, random: Random = Random.Default): LibraryBook? {
        val current = resume(books)?.id
        val available = books.filter { !it.missing && it.id != current }
        val byId = available.associateBy { it.id }
        val fromQueue = queueIds.mapNotNull { byId[it] }
        val pool = fromQueue.ifEmpty { available.filter { it.isUnfinished() } }
        return pool.randomOrNull(random)
    }

    private fun LibraryBook.isUnfinished(): Boolean =
        status != ReadingStatus.FINISHED && progress < LibraryQuery.FINISHED_PROGRESS
}
