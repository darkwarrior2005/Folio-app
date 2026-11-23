package app.folio.core.model

/** What chapter selection needs to know about one chapter. */
data class ChapterFacts(
    val id: Long,
    val read: Boolean,
    val downloaded: Boolean,
    /** Queued or downloading right now. */
    val queued: Boolean,
    val bookmark: Boolean,
    /** The source's chapter number; negative when the source does not give one. */
    val number: Float,
)

/** A rule that picks chapters out of a manga's list. */
enum class ChapterCondition { UNREAD, READ, DOWNLOADED, NOT_DOWNLOADED, BOOKMARKED }

/**
 * Picking chapters for bulk actions. Every function takes the chapters in the order the list
 * shows them, newest first, the way sources list them; reading order is the reverse.
 */
object ChapterSelection {

    fun matching(chapters: List<ChapterFacts>, condition: ChapterCondition): Set<Long> = chapters.filter {
        when (condition) {
            ChapterCondition.UNREAD -> !it.read
            ChapterCondition.READ -> it.read
            ChapterCondition.DOWNLOADED -> it.downloaded
            ChapterCondition.NOT_DOWNLOADED -> !it.downloaded && !it.queued
            ChapterCondition.BOOKMARKED -> it.bookmark
        }
    }.mapTo(mutableSetOf()) { it.id }

    /** Every chapter from [fromId] to [toId] inclusive, in whichever order they were picked. */
    fun between(chapters: List<ChapterFacts>, fromId: Long, toId: Long): Set<Long> {
        val from = chapters.indexOfFirst { it.id == fromId }
        val to = chapters.indexOfFirst { it.id == toId }
        if (from < 0 || to < 0) return emptySet()
        return chapters.subList(minOf(from, to), maxOf(from, to) + 1).mapTo(mutableSetOf()) { it.id }
    }

    /** Chapters numbered from [from] to [to] inclusive; chapters without a number are left out. */
    fun numbered(chapters: List<ChapterFacts>, from: Float, to: Float): Set<Long> {
        val range = minOf(from, to)..maxOf(from, to)
        return chapters.filter { it.number >= 0f && it.number in range }.mapTo(mutableSetOf()) { it.id }
    }

    fun inverted(chapters: List<ChapterFacts>, selected: Set<Long>): Set<Long> =
        chapters.mapNotNullTo(mutableSetOf()) { it.id.takeUnless { id -> id in selected } }

    /**
     * The next [count] unread chapters in reading order that still need downloading, starting
     * after the last chapter read, so chapters skipped long ago are not fetched first.
     */
    fun nextUnreadToDownload(chapters: List<ChapterFacts>, count: Int): List<Long> {
        val readingOrder = chapters.asReversed()
        val lastRead = readingOrder.indexOfLast { it.read }
        return readingOrder.drop(lastRead + 1)
            .filter { !it.read && !it.downloaded && !it.queued }
            .take(count)
            .map { it.id }
    }

    /** Chapters among [ids] that are neither downloaded nor already on their way, in reading order. */
    fun toDownload(chapters: List<ChapterFacts>, ids: Set<Long>): List<Long> =
        chapters.asReversed().filter { it.id in ids && !it.downloaded && !it.queued }.map { it.id }
}
