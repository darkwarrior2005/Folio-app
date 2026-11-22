package app.folio.ui.screens.web

import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.ui.folioViewModel

/** Library -> + -> From the web: off until turned on. Pages / Feeds / Web novels tabs. */
@Composable
fun WebScreen(initialUrl: String?, onBack: () -> Unit, onOpenBook: (Long) -> Unit) {
    val viewModel: WebViewModel = folioViewModel { WebViewModel(it) }
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var pageUrl by remember { mutableStateOf(initialUrl.orEmpty()) }

    LaunchedEffect(task) {
        if (task is WebTaskState.Done) {
            onOpenBook((task as WebTaskState.Done).bookId)
            viewModel.clearTask()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("From the web") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("From the web", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = viewModel::setEnabled)
            }

            if (!enabled) return@Scaffold

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pages") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Feeds") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Web novels") })
            }

            if (task is WebTaskState.Working) CircularProgressIndicator(Modifier.padding(16.dp))
            (task as? WebTaskState.Failed)?.let { Text(it.message, modifier = Modifier.padding(16.dp)) }

            when (tab) {
                0 -> Column(Modifier.padding(16.dp)) {
                    TextField(value = pageUrl, onValueChange = { pageUrl = it }, placeholder = { Text("Paste a link") })
                    Button(onClick = { viewModel.savePage(pageUrl) }) { Text("Save and read") }
                }
                1 -> FeedsTab(viewModel)
                else -> WebNovelsTab(viewModel)
            }
        }
    }
}

@Composable
private fun FeedsTab(viewModel: WebViewModel) {
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    val items by viewModel.feedItems.collectAsStateWithLifecycle()
    var newFeedUrl by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            TextField(value = newFeedUrl, onValueChange = { newFeedUrl = it }, placeholder = { Text("Feed or site address") })
            Button(onClick = { viewModel.addFeed(newFeedUrl); newFeedUrl = "" }) { Text("Add") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(feeds, key = { it.url }) { feed ->
                ListItem(
                    headlineContent = { Text(feed.title) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.openFeed(feed) }) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Refresh, contentDescription = "Load")
                            }
                            IconButton(onClick = { viewModel.removeFeed(feed) }) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    },
                )
            }
            items(items, key = { it.link }) { item ->
                ListItem(
                    headlineContent = { Text(item.title) },
                    supportingContent = item.summary?.let { { Text(it, maxLines = 2) } },
                    trailingContent = {
                        Button(onClick = { viewModel.saveFeedItem(item) }) { Text("Save and read") }
                    },
                )
            }
        }
    }
}

@Composable
private fun WebNovelsTab(viewModel: WebViewModel) {
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            TextField(value = title, onValueChange = { title = it }, placeholder = { Text("Title") })
            TextField(value = url, onValueChange = { url = it }, placeholder = { Text("First chapter address") })
            Button(onClick = { viewModel.startWebNovel(url, title.ifBlank { "Web novel" }) }) { Text("Start") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(novels, key = { it.bookId }) { novel ->
                ListItem(
                    headlineContent = { Text(novel.title) },
                    supportingContent = { Text("${novel.chapters} chapters") },
                    trailingContent = {
                        Button(onClick = { viewModel.checkForNewChapters(novel) }) { Text("Check for new chapters") }
                    },
                )
            }
        }
    }
}
