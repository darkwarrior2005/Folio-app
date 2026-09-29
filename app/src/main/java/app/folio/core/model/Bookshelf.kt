package app.folio.core.model

import kotlin.math.absoluteValue
import kotlin.math.ln

/** A collection as the bookshelf needs it. */
data class ShelfCollection(val id: Long, val name: String)

/** A book's place in a collection. */
data class ShelfLink(val collectionId: Long, val bookId: Long, val position: Int)

/** One shelf: a collection's books in the collection's own order. */
data class Shelf(
    val collectionId: Long,
    val name: String,
    val books: List<LibraryBook>,
)

/**
 * The home bookshelf, built entirely from library data: the books being read, one shelf per
 * collection, and the books that are on no shelf.
 */
data class Bookshelf(
    val reading: List<LibraryBook> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val unsorted: List<LibraryBook> = emptyList(),
) {
    val isEmpty: Boolean get() = reading.isEmpty() && unsorted.isEmpty() && shelves.all { it.books.isEmpty() }
}

object BookshelfBuilder {

    const val READING_LIMIT = 20

    fun build(
        books: List<LibraryBook>,
        collections: List<ShelfCollection>,
        links: List<ShelfLink>,
        now: Long,
    ): Bookshelf {
        val byId = books.associateBy { it.id }
        val collectionIds = collections.mapTo(HashSet()) { it.id }
        val linksByCollection = links.filter { it.collectionId in collectionIds }.groupBy { it.collectionId }

        val shelves = collections.map { collection ->
            Shelf(
                collectionId = collection.id,
                name = collection.name,
                books = linksByCollection[collection.id].orEmpty()
                    .sortedBy { it.position }
                    .mapNotNull { byId[it.bookId] },
            )
        }

        val shelved = linksByCollection.values.flatten().mapTo(HashSet()) { it.bookId }
        val unsorted = books
            .filter { it.id !in shelved }
            .sortedByDescending { it.dateAdded }

        val reading = books
            .filter { !it.missing && LibraryQuery.matchesSmart(it, SmartCollection.CURRENTLY_READING, now) }
            .sortedByDescending { it.lastOpenedAt ?: 0L }
            .take(READING_LIMIT)

        return Bookshelf(reading = reading, shelves = shelves, unsorted = unsorted)
    }
}

/**
 * How a book stands on a shelf. Derived from the book itself so it never changes between visits:
 * thickness follows the page count, height and lean vary a little per book.
 */
data class BookLook(
    /** Share of the shelf's height, 0.8 to 1. */
    val heightFraction: Float,
    /** 0 for a slim book, 1 for a thick one. */
    val thickness: Float,
    /** A slight lean for a few spines; 0 for most books. */
    val tiltDegrees: Float,
    /** Which of the spine decorations to draw. */
    val band: Int,
)

object BookLooks {

    const val BANDS = 3
    private const val MIN_PAGES = 40.0
    private const val MAX_PAGES = 1200.0

    fun of(book: LibraryBook, leanAllowed: Boolean = true): BookLook {
        val hash = mix(book.id)
        val pages = book.pageCount?.takeIf { it > 0 }?.toDouble()
            // No page count (comics folders, text): estimate from the file size, about 60 KB a page.
            ?: (book.fileSize / 60_000.0).coerceAtLeast(MIN_PAGES)
        val thickness = ((ln(pages) - ln(MIN_PAGES)) / (ln(MAX_PAGES) - ln(MIN_PAGES)))
            .coerceIn(0.0, 1.0)
            .toFloat()
        val lean = leanAllowed && hash % 7 == 3L
        return BookLook(
            heightFraction = 0.8f + (hash % 21) / 100f,
            thickness = thickness,
            tiltDegrees = if (lean) (if ((hash / 7) % 2 == 0L) -4f else 4f) else 0f,
            band = ((hash / 21) % BANDS).toInt(),
        )
    }

    /** A stable, well-spread hash of a book id. */
    private fun mix(id: Long): Long {
        var x = id + 0x9E3779B97F4A7C15uL.toLong()
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
        x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
        return (x xor (x ushr 31)).absoluteValue.coerceAtLeast(0L)
    }
}
