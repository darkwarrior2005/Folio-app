package app.folio.core.model

import java.util.Locale

enum class LibraryLayout { GRID, LIST, COMPACT, COVER_ONLY, DETAILED }

enum class BookSort { TITLE, AUTHOR, RECENTLY_OPENED, RECENTLY_ADDED, PROGRESS, FILE_SIZE, YEAR, SERIES, CUSTOM }

enum class LibraryGrouping { NONE, CATEGORY, SERIES, AUTHOR, STATUS, FORMAT }

enum class SmartCollection {
    RECENTLY_ADDED, RECENTLY_READ, CURRENTLY_READING, FINISHED, NEVER_OPENED, FAVORITES, LONG_BOOKS, SHORT_READS
}

data class TagRef(val id: Long, val name: String)

/** The library view of a book: entity fields resolved and joined with its organization. */
data class LibraryBook(
    val id: Long,
    val uri: String,
    val title: String,
    val author: String?,
    val series: String?,
    val volume: Double?,
    val year: Int?,
    val fileName: String,
    val format: BookFormat,
    val status: ReadingStatus,
    val favorite: Boolean,
    val progress: Float,
    val pageCount: Int?,
    val fileSize: Long,
    val dateAdded: Long,
    val lastOpenedAt: Long?,
    val finishedAt: Long?,
    val categoryId: Long?,
    val categoryName: String?,
    val tags: List<TagRef>,
    val collectionIds: Set<Long>,
    val coverPath: String?,
    val missing: Boolean,
    val customOrder: Long,
    val sortTitle: String,
    val queued: Boolean = false,
)

data class LibraryFilter(
    val query: String = "",
    val formats: Set<BookFormat> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),
    val tagIds: Set<Long> = emptySet(),
    val collectionIds: Set<Long> = emptySet(),
    val statuses: Set<ReadingStatus> = emptySet(),
    val authors: Set<String> = emptySet(),
    val series: Set<String> = emptySet(),
    val favoritesOnly: Boolean = false,
    val smart: SmartCollection? = null,
    val minProgress: Float? = null,
    val maxProgress: Float? = null,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || formats.isNotEmpty() || categoryIds.isNotEmpty() ||
            tagIds.isNotEmpty() || collectionIds.isNotEmpty() || statuses.isNotEmpty() ||
            authors.isNotEmpty() || series.isNotEmpty() || favoritesOnly || smart != null ||
            minProgress != null || maxProgress != null

    val activeCount: Int
        get() = listOf(
            formats.isNotEmpty(), categoryIds.isNotEmpty(), tagIds.isNotEmpty(),
            collectionIds.isNotEmpty(), statuses.isNotEmpty(), authors.isNotEmpty(),
            series.isNotEmpty(), favoritesOnly, smart != null,
            minProgress != null || maxProgress != null,
        ).count { it }
}

/**
 * Filtering and sorting for the library. Pure so it can be tested and run off the main thread on
 * the full in-memory list, which stays fast well past a thousand books.
 */
object LibraryQuery {

    const val RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    const val LONG_BOOK_PAGES = 400
    const val SHORT_READ_PAGES = 120
    const val LONG_BOOK_BYTES = 20L * 1024 * 1024
    const val FINISHED_PROGRESS = 0.99f

    fun apply(
        books: List<LibraryBook>,
        filter: LibraryFilter,
        sort: BookSort,
        ascending: Boolean,
        now: Long,
    ): List<LibraryBook> = sort(books.filter { matches(it, filter, now) }, sort, ascending)

    fun matches(book: LibraryBook, filter: LibraryFilter, now: Long): Boolean {
        if (filter.formats.isNotEmpty() && book.format !in filter.formats) return false
        if (filter.categoryIds.isNotEmpty() && book.categoryId !in filter.categoryIds) return false
        if (filter.tagIds.isNotEmpty() && book.tags.none { it.id in filter.tagIds }) return false
        if (filter.collectionIds.isNotEmpty() && filter.collectionIds.none { it in book.collectionIds }) return false
        if (filter.statuses.isNotEmpty() && book.status !in filter.statuses) return false
        if (filter.favoritesOnly && !book.favorite) return false
        if (filter.authors.isNotEmpty() && (book.author == null || book.author !in filter.authors)) return false
        if (filter.series.isNotEmpty() && (book.series == null || book.series !in filter.series)) return false
        filter.minProgress?.let { if (book.progress < it) return false }
        filter.maxProgress?.let { if (book.progress > it) return false }
        filter.smart?.let { if (!matchesSmart(book, it, now)) return false }
        if (filter.query.isNotBlank() && !matchesText(book, filter.query)) return false
        return true
    }

    fun matchesText(book: LibraryBook, query: String): Boolean {
        val terms = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val haystack = buildString {
            append(book.title.lowercase(Locale.ROOT)).append(' ')
            book.author?.let { append(it.lowercase(Locale.ROOT)).append(' ') }
            book.series?.let { append(it.lowercase(Locale.ROOT)).append(' ') }
            book.categoryName?.let { append(it.lowercase(Locale.ROOT)).append(' ') }
            append(book.fileName.lowercase(Locale.ROOT)).append(' ')
            append(book.format.label.lowercase(Locale.ROOT)).append(' ')
            book.tags.forEach { append(it.name.lowercase(Locale.ROOT)).append(' ') }
        }
        return terms.all { haystack.contains(it) }
    }

    fun matchesSmart(book: LibraryBook, smart: SmartCollection, now: Long): Boolean = when (smart) {
        SmartCollection.RECENTLY_ADDED -> now - book.dateAdded <= RECENT_WINDOW_MS
        SmartCollection.RECENTLY_READ -> book.lastOpenedAt?.let { now - it <= RECENT_WINDOW_MS } == true
        SmartCollection.CURRENTLY_READING ->
            book.status == ReadingStatus.READING ||
                (book.status != ReadingStatus.FINISHED && book.progress > 0f && book.progress < FINISHED_PROGRESS)
        SmartCollection.FINISHED -> book.status == ReadingStatus.FINISHED
        SmartCollection.NEVER_OPENED -> book.lastOpenedAt == null
        SmartCollection.FAVORITES -> book.favorite
        SmartCollection.LONG_BOOKS ->
            book.pageCount?.let { it >= LONG_BOOK_PAGES } ?: (book.fileSize >= LONG_BOOK_BYTES)
        SmartCollection.SHORT_READS ->
            book.pageCount?.let { it in 1 until SHORT_READ_PAGES } ?: (book.fileSize < LONG_BOOK_BYTES / 10)
    }

    fun sort(books: List<LibraryBook>, sort: BookSort, ascending: Boolean): List<LibraryBook> {
        val comparator: Comparator<LibraryBook> = when (sort) {
            BookSort.TITLE -> compareBy { it.sortTitle }
            BookSort.AUTHOR -> compareBy(nullsLast()) { it.author?.lowercase(Locale.ROOT) }
            BookSort.RECENTLY_OPENED -> compareBy(nullsFirst()) { it.lastOpenedAt }
            BookSort.RECENTLY_ADDED -> compareBy { it.dateAdded }
            BookSort.PROGRESS -> compareBy { it.progress }
            BookSort.FILE_SIZE -> compareBy { it.fileSize }
            BookSort.YEAR -> compareBy(nullsFirst()) { it.year }
            BookSort.SERIES -> compareBy<LibraryBook, String?>(nullsLast()) { it.series?.lowercase(Locale.ROOT) }
                .thenBy(nullsLast()) { it.volume }
                .thenBy { it.sortTitle }
            BookSort.CUSTOM -> compareBy { it.customOrder }
        }
        val withTieBreak = comparator.thenBy { it.sortTitle }
        // "Recently opened" and "recently added" read newest-first when ascending is off.
        return if (ascending) books.sortedWith(withTieBreak) else books.sortedWith(withTieBreak.reversed())
    }

    fun group(books: List<LibraryBook>, grouping: LibraryGrouping): List<Pair<String?, List<LibraryBook>>> =
        when (grouping) {
            LibraryGrouping.NONE -> listOf(null to books)
            LibraryGrouping.CATEGORY -> books.groupBy { it.categoryName }.toSortedList()
            LibraryGrouping.SERIES -> books.groupBy { it.series }.toSortedList()
            LibraryGrouping.AUTHOR -> books.groupBy { it.author }.toSortedList()
            LibraryGrouping.STATUS -> books.groupBy { it.status.name }.toSortedList()
            LibraryGrouping.FORMAT -> books.groupBy { it.format.label }.toSortedList()
        }

    private fun Map<out String?, List<LibraryBook>>.toSortedList(): List<Pair<String?, List<LibraryBook>>> =
        entries.sortedWith(compareBy(nullsLast()) { it.key?.lowercase(Locale.ROOT) }).map { it.key to it.value }
}
