package app.folio.ui.screens.manga

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.MangaDownloadStatus
import app.folio.ui.components.EmptyState
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons

/** What is downloading now and what is waiting. */
@Composable
fun DownloadQueueScreen(
    onBack: () -> Unit,
    viewModel: DownloadQueueViewModel = folioViewModel { DownloadQueueViewModel(it) },
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // A fresh read each time something in the queue changes, not continuously observed — good
    // enough to tell "about to start" from "blocked on the Wi-Fi-only setting" without a
    // permanently-registered network callback.
    val waitingForWifi = wifiOnly && !isOnUnmeteredNetwork(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manga_downloads)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FolioIcons.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (rows.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearAll) {
                            Text(stringResource(R.string.manga_clear_queue))
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.manga_queue_empty_title),
                message = stringResource(R.string.manga_queue_empty_message),
                icon = FolioIcons.Download,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            items(rows, key = { it.download.chapterId }) { row ->
                ListItem(
                    headlineContent = {
                        Text(row.chapterName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Column {
                            Text(
                                text = row.mangaTitle,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val pageCount = row.download.pageCount ?: 0
                            if (row.download.status == MangaDownloadStatus.DOWNLOADING && pageCount > 0) {
                                LinearProgressIndicator(
                                    progress = { row.download.downloadedPages.toFloat() / pageCount },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.manga_page_of,
                                        row.download.downloadedPages,
                                        pageCount,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            } else {
                                Text(
                                    text = stringResource(
                                        if (waitingForWifi) R.string.manga_waiting_for_wifi else R.string.manga_queued,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.cancel(row.download.chapterId) }) {
                            Icon(FolioIcons.Close, stringResource(R.string.action_cancel))
                        }
                    },
                )
            }
        }
    }
}

/** Whether the current network would satisfy the "Wi-Fi only" download constraint right now. */
private fun isOnUnmeteredNetwork(context: android.content.Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
