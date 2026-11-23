package app.folio.core.model

import app.folio.core.model.ChapterSanitizer.sanitize

/** A chapter as the source currently reports it. */
data class SourceChapter(
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val chapterNumber: Float = -1f,
    val dateUpload: Long = 0,
    /** Position in the source's list, 0 for the first entry it returned. */
    val sourceOrder: Int = 0,
    /** The source's memo for the chapter, as JSON; null when it kept none. */
    val memo: String? = null,
    /** Set by the sync for a new chapter replacing a removed one that was read. */
    val read: Boolean = false,
    /** Set by the sync for a new chapter replacing a removed one that was bookmarked. */
    val bookmark: Boolean = false,
)

/** A chapter already in the library, reduced to the fields a sync compares. */
data class ExistingChapter(
    val id: Long,
    val url: String,
    val name: String,
    val scanlator: String? = null,
    val chapterNumber: Float = -1f,
    val dateUpload: Long = 0,
    val sourceOrder: Int = 0,
    val memo: String? = null,
    val read: Boolean = false,
    val bookmark: Boolean = false,
)

/** What a sync has to write. Chapters the source still lists but has not changed appear nowhere. */
data class ChapterDiff(
    val toInsert: List<SourceChapter> = emptyList(),
    val toUpdate: List<Pair<Long, SourceChapter>> = emptyList(),
    val toDelete: List<Long> = emptyList(),
)

/**
 * The source listed no chapters at all; the stored ones are kept rather than all deleted.
 * [hadChapters] tells a hiccup on a manga that had chapters from a manga the source simply has
 * none of, such as a licensed series taken down, or one only translated into other languages.
 */
class NoChaptersException(val hadChapters: Boolean = true) : IllegalStateException(
    if (hadChapters) {
        "This source returned no chapters this time, so the chapters you have were kept."
    } else {
        "This source lists no chapters for this manga. Sources often take down licensed series, " +
            "and some have chapters only in other languages."
    },
)

/**
 * Works out what changed between the chapters a source lists and the ones already stored.
 *
 * Follows Mihon's chapter sync (Apache-2.0) so a source's list ends up stored the same way:
 * - an empty list is refused ([NoChaptersException]) instead of wiping every chapter, download
 *   and read mark because a site had a bad moment;
 * - the URL identifies a chapter, and a URL listed twice keeps only its first entry;
 * - names are cleaned ([ChapterSanitizer]) and numbers the source left out are recognised from
 *   the names, using the whole list ([ChapterRecognition]);
 * - a new chapter with no upload date gets the newest date seen among the new chapters before it,
 *   or [now], so it sorts sensibly;
 * - a chapter that comes back under a new URL with the number of one the source dropped keeps
 *   that chapter's read and bookmark marks.
 *
 * Folio adds one rule of its own: an unknown upload date or an empty memo never overwrites what
 * was recorded earlier.
 */
object MangaChapterSync {

    /** Cleans and numbers the source's chapters, as Mihon does before comparing them. */
    fun prepare(mangaTitle: String, source: List<SourceChapter>): List<SourceChapter> {
        val listed = source.distinctBy { it.url }.map { chapter ->
            chapter.copy(
                name = chapter.name.sanitize(mangaTitle).ifEmpty { chapter.name.trim() },
                scanlator = chapter.scanlator?.ifBlank { null }?.trim(),
            )
        }
        val numbers = ChapterRecognition.parseChapterNumbers(
            mangaTitle,
            listed.map { ChapterRecognition.Input(it.name, it.chapterNumber.toDouble()) },
        )
        return listed.zip(numbers) { chapter, numbering ->
            val number = numbering.chapter?.let(ChapterRecognition::toDouble) ?: -1.0
            chapter.copy(chapterNumber = number.toFloat())
        }
    }

    fun diff(source: List<SourceChapter>, existing: List<ExistingChapter>, now: Long = 0): ChapterDiff {
        if (source.isEmpty()) throw NoChaptersException(hadChapters = existing.isNotEmpty())

        val listed = source.distinctBy { it.url }
        val existingByUrl = existing.associateBy { it.url }
        val sourceUrls = listed.mapTo(HashSet()) { it.url }

        val toInsert = mutableListOf<SourceChapter>()
        val toUpdate = mutableListOf<Pair<Long, SourceChapter>>()
        // So an undated chapter is never dated after the newer chapters listed before it.
        var maxSeenUploadDate: Long? = null

        listed.forEach { incoming ->
            val current = existingByUrl[incoming.url]
            when {
                current == null -> toInsert += if (incoming.dateUpload == 0L && now != 0L) {
                    incoming.copy(dateUpload = maxSeenUploadDate ?: now)
                } else {
                    if (incoming.dateUpload != 0L) maxSeenUploadDate = maxOf(maxSeenUploadDate ?: 0L, incoming.dateUpload)
                    incoming
                }
                current.differsFrom(incoming) -> toUpdate += current.id to incoming
            }
        }

        val removed = existing.filterNot { it.url in sourceUrls }
        val removedRead = removed.filter { it.read && it.chapterNumber >= 0f }.mapTo(HashSet()) { it.chapterNumber }
        val removedBookmarked = removed.filter { it.bookmark && it.chapterNumber >= 0f }.mapTo(HashSet()) { it.chapterNumber }
        val carriedOver = toInsert.map { chapter ->
            if (chapter.chapterNumber < 0f) return@map chapter
            chapter.copy(
                read = chapter.read || chapter.chapterNumber in removedRead,
                bookmark = chapter.bookmark || chapter.chapterNumber in removedBookmarked,
            )
        }

        return ChapterDiff(toInsert = carriedOver, toUpdate = toUpdate, toDelete = removed.map { it.id })
    }

    /**
     * An upload date of 0 or an empty memo means the source does not know it, so it never
     * overwrites what was recorded earlier.
     */
    private fun ExistingChapter.differsFrom(incoming: SourceChapter): Boolean =
        name != incoming.name ||
            scanlator != incoming.scanlator ||
            chapterNumber != incoming.chapterNumber ||
            sourceOrder != incoming.sourceOrder ||
            (incoming.dateUpload != 0L && dateUpload != incoming.dateUpload) ||
            (incoming.memo != null && memo != incoming.memo)
}
