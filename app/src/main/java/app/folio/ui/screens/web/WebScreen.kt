package app.folio.ui.screens.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.ui.components.BackTopBar
import app.folio.ui.folioViewModel

/** Library -> From the web: off until turned on. Pages / Feeds / Web novels tabs. */
@Composable
fun WebScreen(initialUrl: String?, onBack: () -> Unit, onOpenBook: (Long) -> Unit) {
    val viewModel: WebViewModel = folioViewModel { WebViewModel(it) }
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(task) {
        val done = task as? WebTaskState.Done ?: return@LaunchedEffect
        onOpenBook(done.bookId)
        viewModel.clearTask()
    }

    Scaffold(
        topBar = {
            BackTopBar("From the web", onBack) {
                Switch(
                    checked = enabled,
                    onCheckedChange = viewModel::setEnabled,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!enabled) {
                CenteredMessage(
                    title = "From the web is off",
                    body = "Save web pages, follow RSS and Atom feeds, and read web novels as offline " +
                        "books. Folio only connects to the internet for this while it's turned on.",
                    action = { Button(onClick = { viewModel.setEnabled(true) }) { Text("Turn on") } },
                )
                return@Column
            }

            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Pages") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Feeds") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Web novels") })
            }
            if (task is WebTaskState.Working) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }
            (task as? WebTaskState.Failed)?.let { failed ->
                ErrorCard(failed.message, onDismiss = viewModel::clearTask)
            }

            when (tab) {
                0 -> PagesTab(viewModel, initialUrl, busy = task is WebTaskState.Working)
                1 -> FeedsTab(viewModel, busy = task is WebTaskState.Working)
                else -> WebNovelsTab(viewModel, busy = task is WebTaskState.Working)
            }
        }
    }
}

@Composable
private fun PagesTab(viewModel: WebViewModel, initialUrl: String?, busy: Boolean) {
    var pageUrl by rememberSaveable { mutableStateOf(initialUrl.orEmpty()) }
    fun save() {
        if (pageUrl.isNotBlank() && !busy) viewModel.savePage(pageUrl.trim())
    }
    Column(Modifier.padding(16.dp)) {
        Text(
            "Paste a link to an article. Folio keeps the readable part and its pictures as a book you can read offline.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        UrlField(pageUrl, { pageUrl = it }, label = "Page address", onDone = ::save)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = ::save, enabled = pageUrl.isNotBlank() && !busy) { Text("Save and read") }
        }
    }
}

@Composable
private fun FeedsTab(viewModel: WebViewModel, busy: Boolean) {
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    val feedItems by viewModel.feedItems.collectAsStateWithLifecycle()
    var newFeedUrl by rememberSaveable { mutableStateOf("") }
    fun add() {
        if (newFeedUrl.isNotBlank() && !busy) {
            viewModel.addFeed(newFeedUrl.trim())
            newFeedUrl = ""
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Add a feed's address, or a website's - Folio finds its feed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                UrlField(newFeedUrl, { newFeedUrl = it }, label = "Feed or website address", onDone = ::add)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = ::add, enabled = newFeedUrl.isNotBlank() && !busy) { Text("Add feed") }
                }
            }
        }
        sectionHeader("Your feeds")
        if (feeds.isEmpty()) emptyLine("None yet.")
        items(feeds, key = { "feed:" + it.url }) { feed ->
            ListItem(
                headlineContent = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(feed.siteUrl ?: feed.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { viewModel.openFeed(feed) }, enabled = !busy) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Show items")
                        }
                        IconButton(onClick = { viewModel.removeFeed(feed) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove feed")
                        }
                    }
                },
            )
        }
        if (feedItems.isNotEmpty()) {
            sectionHeader("Items")
            items(feedItems, key = { "item:" + it.link }) { item ->
                ListItem(
                    headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = item.summary?.let { summary ->
                        { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.saveFeedItem(item) }, enabled = !busy) {
                            Icon(Icons.Rounded.Download, contentDescription = "Save and read")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun WebNovelsTab(viewModel: WebViewModel, busy: Boolean) {
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    fun start() {
        if (url.isNotBlank() && !busy) {
            viewModel.startWebNovel(url.trim(), title.trim().ifBlank { "Web novel" })
            url = ""
            title = ""
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Give the address of a chapter. Folio saves it and the next 10 chapters as one book, and can add new chapters later.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(8.dp))
                UrlField(url, { url = it }, label = "First chapter address", onDone = ::start)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = ::start, enabled = url.isNotBlank() && !busy) { Text("Save chapters") }
                }
            }
        }
        sectionHeader("Your web novels")
        if (novels.isEmpty()) emptyLine("None yet.")
        items(novels, key = { it.bookId }) { novel ->
            ListItem(
                headlineContent = { Text(novel.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${novel.chapters} chapters") },
                trailingContent = {
                    IconButton(onClick = { viewModel.checkForNewChapters(novel) }, enabled = !busy) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Check for new chapters")
                    }
                },
            )
        }
    }
}

@Composable
private fun UrlField(value: String, onValueChange: (String) -> Unit, label: String, onDone: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text("https://") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

private fun LazyListScope.sectionHeader(title: String) {
    item(key = "header:$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        )
    }
}

private fun LazyListScope.emptyLine(text: String) {
    item(key = "empty:$text") {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CenteredMessage(title: String, body: String, action: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Public,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
