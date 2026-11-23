package app.folio.ui.screens.manga

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import app.folio.manga.source.SourceCoverCallFactory
import app.folio.ui.icons.FolioIcons
import app.folio.ui.theme.ensureReadable
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import java.io.File

/**
 * A manga cover, preferring a locally cached file over the source's URL.
 *
 * With [sourceId], the cover is fetched through that source's own client and headers (see
 * SourceCoverCallFactory), so sites that refuse outside image requests still show their covers.
 */
@Composable
fun MangaCover(
    url: String?,
    localPath: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    sourceId: Long? = null,
) {
    val local = localPath?.let { File(it) }?.takeIf { it.exists() }
    val model: Any? = local ?: url
    val context = LocalPlatformContext.current
    val request: Any? = remember(model, sourceId) {
        if (local == null && url != null && sourceId != null) {
            ImageRequest.Builder(context)
                .data(url)
                .httpHeaders(
                    NetworkHeaders.Builder()
                        .set(SourceCoverCallFactory.SOURCE_HEADER, sourceId.toString())
                        .build(),
                )
                .build()
        } else {
            model
        }
    }

    if (model == null) {
        // The card behind this is surfaceVariant; onSurfaceVariant isn't guaranteed to read
        // against it on every one of the app's themes, so nudge it the same way the theme
        // builder nudges an accent color, rather than trusting the role pairing blindly.
        val surface = MaterialTheme.colorScheme.surfaceVariant
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val tint = ensureReadable(MaterialTheme.colorScheme.onSurfaceVariant, surface, isDark)
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = FolioIcons.MenuBook,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
        return
    }

    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.fillMaxSize(),
    )
}
