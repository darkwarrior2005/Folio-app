package app.folio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.folio.core.model.BookFormat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File
import kotlin.math.absoluteValue

/**
 * A book's cover, or a generated one when the file has none. Generated covers use a colour
 * derived from the title so a shelf of coverless books still looks deliberate and is scannable.
 */
@Composable
fun BookCover(
    title: String,
    coverPath: String?,
    format: BookFormat,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp),
) {
    val exists = remember(coverPath) { coverPath != null && File(coverPath).exists() }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (exists) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(File(coverPath!!))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            GeneratedCover(title = title, format = format)
        }
    }
}

@Composable
private fun GeneratedCover(title: String, format: BookFormat) {
    val palette = coverPalette(title)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(palette)),
    ) {
        val compact = maxWidth < 90.dp
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (compact) 6.dp else 12.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.95f),
                style = MaterialTheme.typography.titleSmall,
                fontSize = if (compact) 10.sp else 14.sp,
                lineHeight = if (compact) 13.sp else 18.sp,
                maxLines = if (compact) 3 else 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (compact) 6.dp else 12.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Text(
                text = format.label,
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (compact) 8.sp else 10.sp,
                textAlign = TextAlign.End,
            )
        }
    }
}

private fun coverPalette(seed: String): List<Color> {
    val hash = seed.hashCode().absoluteValue
    val palettes = listOf(
        listOf(Color(0xFF2F4858), Color(0xFF33658A)),
        listOf(Color(0xFF5B4B8A), Color(0xFF7768AE)),
        listOf(Color(0xFF7A4B35), Color(0xFFA9714B)),
        listOf(Color(0xFF2C5F2D), Color(0xFF497B4B)),
        listOf(Color(0xFF6B2737), Color(0xFF8E4054)),
        listOf(Color(0xFF1F3A5F), Color(0xFF3C6E9B)),
        listOf(Color(0xFF4A4A4A), Color(0xFF6E6E6E)),
        listOf(Color(0xFF2D4739), Color(0xFF4F7359)),
    )
    return palettes[hash % palettes.size]
}
