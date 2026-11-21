package app.folio.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.folio.core.model.LibraryBook
import java.io.File

/** Resume where you left off, or ask for Surprise me (dice icon) when there's nothing to resume. */
@Composable
fun WelcomeBackCard(
    resumeBook: LibraryBook?,
    onResume: (Long) -> Unit,
    onSurprise: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (resumeBook != null) {
                val cover = resumeBook.coverPath?.let { path ->
                    runCatching { android.graphics.BitmapFactory.decodeFile(path) }.getOrNull()
                }
                if (cover != null) {
                    Image(
                        bitmap = cover.asImageBitmap(),
                        contentDescription = resumeBook.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 48.dp, height = 68.dp).clip(RoundedCornerShape(6.dp)),
                    )
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("Welcome back", style = MaterialTheme.typography.labelMedium)
                    Text(resumeBook.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(
                        progress = { resumeBook.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Row(Modifier.padding(top = 8.dp)) {
                        Button(onClick = { onResume(resumeBook.id) }) { Text("Continue") }
                        OutlinedButton(onClick = onSurprise, modifier = Modifier.padding(start = 8.dp)) {
                            Icon(Icons.Rounded.Casino, contentDescription = "Surprise me")
                        }
                    }
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text("Nothing in progress", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Let us surprise you with something to read",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onSurprise) {
                    Icon(Icons.Rounded.Casino, contentDescription = null)
                    Text("Surprise me", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}
