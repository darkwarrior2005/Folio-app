package app.folio.core.model

import kotlin.random.Random

/** Pure picks for the Welcome back card: resume, or surprise me with something else to read. */
object ComfortPicks {

    /** The book being read right now: in progress, most recently opened. */
    fun resume(books: List<LibraryBook>): LibraryBook? = books
        .filter { !it.missing && it.finishedAt == null && it.progress > 0f && it.progress < 1f }
        .maxByOrNull { it.lastOpenedAt ?: it.dateAdded }

    /**
     * Picks something other than the book currently being read: from the reading queue first (in
     * queue order), else a random unfinished book. Never returns [current].
     */
    fun surprise(books: List<LibraryBook>, queueBookIds: List<Long>, random: Random = Random, current: LibraryBook? = resume(books)): LibraryBook? {
        val byId = books.associateBy { it.id }
        val fromQueue = queueBookIds
            .mapNotNull { byId[it] }
            .firstOrNull { !it.missing && it.finishedAt == null && it.id != current?.id }
        if (fromQueue != null) return fromQueue

        val candidates = books.filter { !it.missing && it.finishedAt == null && it.id != current?.id }
        if (candidates.isEmpty()) return null
        return candidates[random.nextInt(candidates.size)]
    }
}
