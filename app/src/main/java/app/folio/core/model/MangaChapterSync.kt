package app.folio.core.model

import app.folio.core.model.ChapterSanitizer.sanitize
import eu.kanade.tachiyomi.source.model.SChapter

/**
 * Reconciles a source's freshly fetched chapter list with what the library already has for a
 * manga. Pure logic, no I/O - the repository decides how to apply the result.
 *
 * Rules (following Mihon's):
 * - A chapter is identified by its URL: renaming or reordering keeps its read/bookmark state.
 * - Duplicate URLs in what the source returned are dropped, first one wins.
 * - Names are run through [ChapterSanitizer], and a missing chapter number is filled in by
 *   [ChapterRecognition] using every chapter's name together.
 * - An undated new chapter gets the newest upload date seen before it in the list, or now if
 *   there is none.
 * - A chapter whose URL changed but whose number matches a chapter that's no longer in the
 *   fetched list keeps that old chapter's read and bookmark marks.
 * - An empty fetched list never wipes existing chapters: it throws [NoChaptersException] instead.
 */
object MangaChapterSync {

    data class Result(
        val toInsert: List<MangaChapter>,
        val toUpdate: List<MangaChapter>,
        val toDeleteIds: List<Long>,
    )

    fun sync(
        mangaTitle: String,
        existing: List<MangaChapter>,
        fetched: List<SChapter>,
        now: Long,
    ): Result {
        if (fetched.isEmpty()) {
            throw NoChaptersException(hadChapters = existing.isNotEmpty())
        }

        val deduped = fetched.distinctBy { it.url }
        val existingByUrl = existing.associateBy { it.url }
        val unmatchedExisting = existing.filter { it.url !in deduped.map(SChapter::url).toSet() }
        val unmatchedByNumber = unmatchedExisting.associateBy { it.chapterNumber }

        val numbering = ChapterRecognition.parseChapterNumbers(
            mangaTitle,
            deduped.map { ChapterRecognition.RecognitionChapter(it.name, it.chapter_number.toDouble()) },
        )

        var newestDateSeen = 0L
        val merged = deduped.mapIndexed { index, source ->
            val number = numbering[index].chapter?.let(ChapterRecognition::toDouble)?.toFloat()
                ?: source.chapter_number
            val cleanName = source.name.sanitize(mangaTitle).ifBlank { source.name }
            val uploadDate = if (source.date_upload > 0) {
                newestDateSeen = source.date_upload
                source.date_upload
            } else {
                newestDateSeen.takeIf { it > 0 } ?: now
            }

            val byUrl = existingByUrl[source.url]
            val carryOver = byUrl ?: unmatchedByNumber[number]

            MangaChapter(
                id = byUrl?.id ?: 0,
                mangaId = existing.firstOrNull()?.mangaId ?: 0,
                url = source.url,
                name = cleanName,
                scanlator = source.scanlator,
                read = carryOver?.read ?: false,
                bookmark = carryOver?.bookmark ?: false,
                lastPageRead = carryOver?.lastPageRead ?: 0,
                pagesCount = carryOver?.pagesCount ?: 0,
                chapterNumber = number,
                dateUpload = uploadDate,
                dateFetch = byUrl?.dateFetch ?: now,
                sourceOrder = index,
            )
        }

        val toInsert = merged.filter { it.id == 0L }
        val toUpdate = merged.filter { it.id != 0L }
        val toDeleteIds = unmatchedExisting
            .filterNot { it.chapterNumber in merged.map(MangaChapter::chapterNumber) }
            .map { it.id }

        return Result(toInsert, toUpdate, toDeleteIds)
    }
}
