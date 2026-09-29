package app.folio.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.core.model.BookLook
import app.folio.core.model.BookLooks
import app.folio.core.model.Bookshelf
import app.folio.core.model.LibraryBook
import app.folio.core.model.SmartCollection
import app.folio.ui.components.BookCover
import app.folio.ui.components.coverPalette
import app.folio.ui.theme.LocalFolioTheme
import app.folio.ui.theme.LocalReduceMotion
import app.folio.ui.util.Format
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.io.File

/** Sizes for one screen width. Books keep a usable size; long shelves scroll instead of shrinking. */
@Immutable
internal data class ShelfMetrics(
    val bookHeight: Dp,
    val headroom: Dp,
    val spineMin: Dp,
    val spineMax: Dp,
    val plankHeight: Dp,
)

private const val COVER_ASPECT = 0.68f

internal fun shelfMetrics(contentWidth: Dp): ShelfMetrics = when {
    contentWidth < 320.dp -> ShelfMetrics(
        bookHeight = 108.dp, headroom = 14.dp, spineMin = 20.dp, spineMax = 32.dp, plankHeight = 40.dp,
    )
    contentWidth < 560.dp -> ShelfMetrics(
        bookHeight = 128.dp, headroom = 16.dp, spineMin = 22.dp, spineMax = 38.dp, plankHeight = 40.dp,
    )
    else -> ShelfMetrics(
        bookHeight = 164.dp, headroom = 18.dp, spineMin = 26.dp, spineMax = 46.dp, plankHeight = 44.dp,
    )
}

/** Wood tones, nudged toward the theme's accent so each of the app's themes keeps its character. */
@Immutable
internal data class ShelfPalette(
    val frameTop: Color,
    val frameBottom: Color,
    val backTop: Color,
    val backBottom: Color,
    val plankLip: Color,
    val plankTop: Color,
    val plankFront: Color,
    val plankBottom: Color,
    val label: Color,
    val engrave: Color,
    val hint: Color,
)

@Composable
internal fun rememberShelfPalette(): ShelfPalette {
    val dark = LocalFolioTheme.current.isDark
    val surface = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary
    return remember(dark, surface, accent) {
        fun wood(argb: Long) = lerp(Color(argb), accent, 0.08f)
        if (dark) {
            ShelfPalette(
                frameTop = wood(0xFF4A3628),
                frameBottom = wood(0xFF33251C),
                backTop = lerp(Color(0xFF1C1612), surface, 0.35f),
                backBottom = lerp(Color(0xFF2A2019), surface, 0.3f),
                plankLip = wood(0xFF86664F),
                plankTop = wood(0xFF6B503E),
                plankFront = wood(0xFF533D2F),
                plankBottom = wood(0xFF3E2D22),
                label = Color(0xFFF2E4CF),
                engrave = Color.Black.copy(alpha = 0.5f),
                hint = Color(0xFFCDBBA5),
            )
        } else {
            ShelfPalette(
                frameTop = wood(0xFFC29C73),
                frameBottom = wood(0xFFA27B53),
                backTop = lerp(Color(0xFFE7D8C2), surface, 0.3f),
                backBottom = lerp(Color(0xFFD8C3A4), surface, 0.25f),
                plankLip = wood(0xFFEBD5B4),
                plankTop = wood(0xFFDDBF97),
                plankFront = wood(0xFFC9A577),
                plankBottom = wood(0xFFAB865B),
                label = Color(0xFF3A2716),
                engrave = Color.White.copy(alpha = 0.5f),
                hint = Color(0xFF6B5641),
            )
        }
    }
}

/**
 * The bookcase, top to bottom: the books being read, one shelf per collection, and last a shelf
 * of the books that are in no collection.
 */
@Composable
internal fun Bookcase(
    shelf: Bookshelf,
    metrics: ShelfMetrics,
    onOpenBook: (Long) -> Unit,
    onBookDetails: (Long) -> Unit,
    onOpenCollection: (Long) -> Unit,
    onOpenSmart: (SmartCollection) -> Unit,
    onCreateShelf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberShelfPalette()
    // Mostly spines, with the first book and the odd covered one turned face out.
    val collectionFaceOut: (Int, LibraryBook) -> Boolean =
        { index, book -> index == 0 || (index % 6 == 4 && book.coverPath != null) }

    Box(modifier.fillMaxWidth()) {
        BookcaseFrame(palette) {
            if (shelf.reading.isNotEmpty()) {
                ShelfRow(
                    title = stringResource(R.string.smart_currently_reading),
                    books = shelf.reading,
                    metrics = metrics,
                    palette = palette,
                    // What you're reading faces out: it is what you most likely want next.
                    faceOut = { index, _ -> index < 3 },
                    onOpenShelf = { onOpenSmart(SmartCollection.CURRENTLY_READING) },
                    onOpenBook = onOpenBook,
                    onBookDetails = onBookDetails,
                )
            }
            shelf.shelves.forEach { entry ->
                key(entry.collectionId) {
                    ShelfRow(
                        title = entry.name,
                        books = entry.books,
                        metrics = metrics,
                        palette = palette,
                        faceOut = collectionFaceOut,
                        onOpenShelf = { onOpenCollection(entry.collectionId) },
                        onOpenBook = onOpenBook,
                        onBookDetails = onBookDetails,
                        emptyHint = stringResource(R.string.home_shelf_empty),
                    )
                }
            }
            if (shelf.shelves.isEmpty()) {
                ShelfRow(
                    title = stringResource(R.string.home_first_shelf),
                    books = emptyList(),
                    metrics = metrics,
                    palette = palette,
                    faceOut = { _, _ -> false },
                    onOpenShelf = onCreateShelf,
                    onOpenBook = onOpenBook,
                    onBookDetails = onBookDetails,
                    emptyHint = stringResource(R.string.home_first_shelf_hint),
                )
            }
            // Always the bottom shelf: only the books that belong to no collection.
            if (shelf.unsorted.isNotEmpty()) {
                ShelfRow(
                    title = stringResource(R.string.home_other_books),
                    books = shelf.unsorted,
                    metrics = metrics,
                    palette = palette,
                    faceOut = collectionFaceOut,
                    onOpenShelf = { onOpenSmart(SmartCollection.UNSORTED) },
                    onOpenBook = onOpenBook,
                    onBookDetails = onBookDetails,
                )
            }
        }
    }
}

@Composable
private fun BookcaseFrame(palette: ShelfPalette, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .background(Brush.verticalGradient(listOf(palette.frameTop, palette.frameBottom)))
            .padding(start = 7.dp, end = 7.dp, top = 8.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp)),
        content = content,
    )
}

@Composable
private fun ShelfRow(
    title: String,
    books: List<LibraryBook>,
    metrics: ShelfMetrics,
    palette: ShelfPalette,
    faceOut: (Int, LibraryBook) -> Boolean,
    onOpenShelf: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onBookDetails: (Long) -> Unit,
    emptyHint: String? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(metrics.bookHeight + metrics.headroom)
                .background(Brush.verticalGradient(listOf(palette.backTop, palette.backBottom))),
        ) {
            if (books.isEmpty()) {
                Text(
                    text = emptyHint.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.hint,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    itemsIndexed(books, key = { _, book -> book.id }) { index, book ->
                        ShelfBook(
                            book = book,
                            faceOut = faceOut(index, book),
                            metrics = metrics,
                            onOpen = onOpenBook,
                            onDetails = onBookDetails,
                        )
                    }
                }
            }
            // The shelf above casts a soft shadow onto the back of this one.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.16f), Color.Transparent))),
            )
        }
        Plank(title = title, palette = palette, metrics = metrics, onClick = onOpenShelf)
    }
}

/** The shelf board, with the collection's name set into it. Tapping it opens the collection. */
@Composable
private fun Plank(title: String, palette: ShelfPalette, metrics: ShelfMetrics, onClick: () -> Unit) {
    val style = MaterialTheme.typography.headlineSmall.merge(
        TextStyle(shadow = Shadow(color = palette.engrave, offset = Offset(0f, 1.5f), blurRadius = 0f)),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = metrics.plankHeight)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(palette.plankTop, palette.plankFront, palette.plankBottom)))
                drawRect(palette.plankLip, size = Size(size.width, 3.dp.toPx()))
                val line = 1.dp.toPx()
                drawRect(
                    Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(0f, size.height - line),
                    size = Size(size.width, line),
                )
            }
            .clickable(
                onClickLabel = stringResource(R.string.home_open_shelf, title),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { heading() }
            .padding(horizontal = 14.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = style,
            color = palette.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShelfBook(
    book: LibraryBook,
    faceOut: Boolean,
    metrics: ShelfMetrics,
    onOpen: (Long) -> Unit,
    onDetails: (Long) -> Unit,
) {
    val look = remember(book.id, book.pageCount, book.fileSize, faceOut) { BookLooks.of(book, leanAllowed = !faceOut) }
    val interaction = remember { MutableInteractionSource() }
    val lift by rememberPressLift(interaction)
    val height = if (faceOut) metrics.bookHeight * 0.94f else metrics.bookHeight * look.heightFraction
    val width = if (faceOut) {
        height * COVER_ASPECT
    } else {
        androidx.compose.ui.unit.lerp(metrics.spineMin, metrics.spineMax, look.thickness)
    }
    val shape = if (faceOut) {
        RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 5.dp, bottomEnd = 5.dp)
    } else {
        RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp)
    }

    Box(
        Modifier
            // A leaning book needs a little room so it does not rest on its neighbour.
            .padding(horizontal = if (look.tiltDegrees != 0f) 6.dp else 0.dp)
            .size(width, height)
            .graphicsLayer {
                rotationZ = look.tiltDegrees
                transformOrigin = TransformOrigin(if (look.tiltDegrees < 0f) 0f else 1f, 1f)
                translationY = -lift * 6.dp.toPx()
                scaleX = 1f + lift * 0.03f
                scaleY = 1f + lift * 0.03f
                alpha = if (book.missing) 0.55f else 1f
            }
            .shadow(if (faceOut) 4.dp else 2.dp, shape, clip = false)
            .bookClicks(book, interaction, onOpen, onDetails)
            .clip(shape),
    ) {
        if (faceOut) FaceOutCover(book) else Spine(book, look)
    }
}

/** A book standing face out: its real cover, a hinge shadow, and how far you've read. */
@Composable
private fun FaceOutCover(book: LibraryBook) {
    Box(Modifier.fillMaxSize()) {
        BookCover(
            title = book.title,
            coverPath = book.coverPath,
            format = book.format,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
        )
        Box(
            Modifier
                .fillMaxHeight()
                .width(7.dp)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.3f), Color.Transparent))),
        )
        if (book.progress > 0.005f && book.progress < 0.995f) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(book.progress)
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * A book seen from its spine: a strip of its cover (or its generated colour), shaded like a
 * rounded spine, with the title running down it.
 */
@Composable
private fun Spine(book: LibraryBook, look: BookLook) {
    val colors = remember(book.title) { coverPalette(book.title) }
    val hasCover = remember(book.coverPath) { book.coverPath != null && File(book.coverPath).exists() }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.first()),
    ) {
        if (hasCover) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(File(book.coverPath!!)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val rounding = Brush.horizontalGradient(
                        0f to Color.White.copy(alpha = 0.22f),
                        0.2f to Color.Transparent,
                        0.7f to Color.Black.copy(alpha = 0.08f),
                        1f to Color.Black.copy(alpha = 0.36f),
                    )
                    // Covers vary wildly; a steady scrim keeps the title readable on any of them.
                    val scrim = Color.Black.copy(alpha = if (hasCover) 0.4f else 0.1f)
                    val gilt = Color(0xFFE9D3A3).copy(alpha = 0.75f)
                    val thin = 1.2.dp.toPx()
                    onDrawBehind {
                        drawRect(scrim)
                        drawRect(rounding)
                        when (look.band) {
                            0 -> {
                                drawRect(gilt, Offset(0f, size.height * 0.07f), Size(size.width, thin))
                                drawRect(gilt, Offset(0f, size.height * 0.07f + thin * 2.5f), Size(size.width, thin))
                                drawRect(gilt, Offset(0f, size.height * 0.9f), Size(size.width, thin))
                            }
                            1 -> drawRect(
                                gilt.copy(alpha = 0.35f),
                                Offset(0f, size.height * 0.08f),
                                Size(size.width, size.height * 0.07f),
                            )
                            else -> Unit
                        }
                    }
                },
        )
        Text(
            text = book.title,
            color = Color.White.copy(alpha = 0.95f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(vertical = 12.dp)
                .readDownward(),
        )
    }
}

/** Lays text out along the height of its box, reading top to bottom like an English spine. */
private fun Modifier.readDownward(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(maxWidth = constraints.maxHeight, maxHeight = constraints.maxWidth),
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = -(placeable.width - placeable.height) / 2,
            y = (placeable.width - placeable.height) / 2,
        ) { rotationZ = 90f }
    }
}

/** A book rises slightly while pressed, instead of a ripple across its cover. */
@Composable
private fun rememberPressLift(interaction: MutableInteractionSource): State<Float> {
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = LocalReduceMotion.current
    return animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 1f else 0f,
        animationSpec = tween(if (pressed) 90 else 160),
        label = "book-lift",
    )
}

/** Tap opens the book in its reader; long press opens its details. Spoken as one button. */
@Composable
private fun Modifier.bookClicks(
    book: LibraryBook,
    interaction: MutableInteractionSource,
    onOpen: (Long) -> Unit,
    onDetails: (Long) -> Unit,
): Modifier {
    val description = bookDescription(book)
    val detailsLabel = stringResource(R.string.home_book_details)
    val reduceMotion = LocalReduceMotion.current
    return this
        .combinedClickable(
            interactionSource = interaction,
            // With motion reduced there is no lift, so keep the usual press feedback.
            indication = if (reduceMotion) LocalIndication.current else null,
            onLongClickLabel = detailsLabel,
            onLongClick = { onDetails(book.id) },
            onClick = { onOpen(book.id) },
        )
        .clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick(label = null) { onOpen(book.id); true }
            onLongClick(label = detailsLabel) { onDetails(book.id); true }
        }
}

@Composable
private fun bookDescription(book: LibraryBook): String {
    val parts = mutableListOf(book.title)
    book.author?.takeIf { it.isNotBlank() }?.let { parts += stringResource(R.string.home_book_by, it) }
    val percent = Format.percent(book.progress)
    if (percent in 1..99) parts += stringResource(R.string.home_book_read, percent)
    if (book.missing) parts += stringResource(R.string.home_book_missing)
    return parts.joinToString(", ")
}
