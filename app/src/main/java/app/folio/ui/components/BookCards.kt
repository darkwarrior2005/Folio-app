package app.folio.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.core.model.LibraryBook
import app.folio.core.model.ReadingStatus
import app.folio.data.settings.LibrarySettings
import app.folio.ui.util.Format

private const val COVER_RATIO = 0.66f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridCard(
    book: LibraryBook,
    settings: LibrarySettings,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
    ) {
        Box {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                format = book.format,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(COVER_RATIO),
            )
            CoverBadges(book, settings, selected, selectionMode, Modifier.align(Alignment.TopEnd))
            if (settings.showProgress && book.progress > 0f) {
                ProgressBar(
                    progress = book.progress,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                )
            }
        }
        if (settings.showTitles) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = book.title,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (settings.showAuthor && book.author != null) {
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CoverBadges(
    book: LibraryBook,
    settings: LibrarySettings,
    selected: Boolean,
    selectionMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (book.missing) {
            BadgeIcon(Icons.Rounded.WarningAmber, MaterialTheme.colorScheme.error)
        }
        if (book.favorite) {
            BadgeIcon(Icons.Rounded.Star, MaterialTheme.colorScheme.primary)
        }
        if (book.status == ReadingStatus.FINISHED) {
            BadgeIcon(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary)
        }
        if (settings.showFileType) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(
                    text = book.format.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
        if (selectionMode) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun BadgeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListRow(
    book: LibraryBook,
    settings: LibrarySettings,
    selected: Boolean,
    selectionMode: Boolean,
    detailed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(
            title = book.title,
            coverPath = book.coverPath,
            format = book.format,
            modifier = Modifier
                .width(if (detailed) 64.dp else 48.dp)
                .aspectRatio(COVER_RATIO),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (detailed) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (settings.showAuthor) {
                Text(
                    text = book.author ?: androidx.compose.ui.res.stringResource(R.string.unknown_author),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (detailed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        if (settings.showFileType) append(book.format.label).append(" · ")
                        append(Format.fileSize(book.fileSize))
                        book.pageCount?.let { append(" · ").append(it).append(" pages") }
                        Format.relativeDate(book.lastOpenedAt)?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (settings.showTags && book.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    book.tags.take(3).forEach { tag ->
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (settings.showProgress && book.progress > 0f) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressBar(progress = book.progress, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${Format.percent(book.progress)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (book.missing) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.missing_file_badge),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (book.favorite) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (selectionMode) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
