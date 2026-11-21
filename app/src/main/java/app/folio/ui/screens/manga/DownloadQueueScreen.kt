package app.folio.ui.screens.manga

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.manga.download.DownloadProgress
import app.folio.ui.LocalContainer

@Composable
fun DownloadQueueScreen(onBack: () -> Unit) {
    val container = LocalContainer.current
    val queue by container.downloadManager.queue.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Downloads") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(queue, key = { it.chapter.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.chapter.name) },
                    supportingContent = {
                        when (val progress = item.progress) {
                            is DownloadProgress.PageProgress -> LinearProgressIndicator(
                                progress = { progress.completed.toFloat() / progress.total.coerceAtLeast(1) },
                            )
                            is DownloadProgress.Failed -> Text("Failed: ${progress.reason}")
                            is DownloadProgress.Done -> Text("Done")
                            null -> Text("Waiting for Wi-Fi")
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { container.downloader.cancel(item.chapter.id) }) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    },
                )
            }
        }
    }
}
