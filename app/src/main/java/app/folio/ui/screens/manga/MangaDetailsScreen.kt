package app.folio.ui.screens.manga

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.model.MangaChapter
import app.folio.ui.folioViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MangaDetailsScreen(
    mangaId: Long,
    onBack: () -> Unit,
    onReadChapter: (Long, Long) -> Unit,
    onNeedsWebView: (Long, String) -> Unit,
) {
    val viewModel: MangaDetailsViewModel = folioViewModel(key = "details-$mangaId") {
        MangaDetailsViewModel(it, mangaId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.manga?.title ?: "") },
                actions = {
                    IconButton(onClick = { viewModel.toggleFavorite() }) {
                        Icon(
                            if (state.manga?.favorite == true) {
                                androidx.compose.material.icons.Icons.Filled.Favorite
                            } else {
                                androidx.compose.material.icons.Icons.Filled.FavoriteBorder
                            },
                            contentDescription = "In library",
                        )
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.refreshing) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.refreshError?.let { error ->
                RefreshErrorCard(
                    error = error,
                    onRetry = { viewModel.refresh() },
                    onOpenSite = { state.manga?.let { onNeedsWebView(it.sourceId, it.url) } },
                )
            }

            if (selected.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    Text("${selected.size} selected")
                    Button(onClick = { viewModel.download(selected.toList()); selected = emptySet() }) { Text("Download") }
                    Button(onClick = { viewModel.setRead(selected.toList(), true); selected = emptySet() }) { Text("Mark read") }
                    Button(onClick = { viewModel.setBookmarked(selected.toList(), true); selected = emptySet() }) { Text("Bookmark") }
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(state.chapters, key = { it.id }) { chapter ->
                    ChapterRow(
                        chapter = chapter,
                        downloaded = viewModel.isDownloaded(chapter),
                        isSelected = chapter.id in selected,
                        onClick = {
                            if (selected.isEmpty()) {
                                state.manga?.let { onReadChapter(mangaId, chapter.id) }
                            } else {
                                selected = toggle(selected, chapter.id)
                            }
                        },
                        onLongClick = { selected = toggle(selected, chapter.id) },
                    )
                }
            }
        }
    }
}

private fun toggle(set: Set<Long>, id: Long): Set<Long> = if (id in set) set - id else set + id

@Composable
private fun ChapterRow(
    chapter: MangaChapter,
    downloaded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(chapter.name) },
        supportingContent = {
            Text(if (downloaded) "Downloaded" else "", style = MaterialTheme.typography.labelSmall)
        },
        trailingContent = {
            if (chapter.bookmark) {
                Icon(androidx.compose.material.icons.Icons.Filled.Bookmark, contentDescription = "Bookmarked")
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (chapter.read) {
            androidx.compose.material3.ListItemDefaults.colors(
                headlineColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            androidx.compose.material3.ListItemDefaults.colors()
        },
    )
}

@Composable
private fun RefreshErrorCard(error: RefreshError, onRetry: () -> Unit, onOpenSite: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(error.message, style = MaterialTheme.typography.bodyMedium)
            Row {
                Button(onClick = onRetry) { Text("Try again") }
                Button(onClick = onOpenSite) { Text("Open the site") }
            }
        }
    }
}
