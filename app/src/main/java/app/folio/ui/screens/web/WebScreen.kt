package app.folio.ui.screens.web

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.data.settings.SavedFeed
import app.folio.data.settings.WebNovel
import app.folio.ui.components.EmptyState
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons
import app.folio.ui.theme.LocalSpacing
import app.folio.ui.util.Format
import app.folio.web.FeedItem

/**
 * "From the web": save a page for offline reading, follow feeds, and follow web novels, all of it
 * turned into books in the library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    viewModel: WebViewModel = folioViewModel { WebViewModel(it) },
) {
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val openFeed by viewModel.openFeed.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val shared = remember { viewModel.takeSharedUrl() }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = context.getString(message.text)
            snackbar.showSnackbar(if (message.detail.isNullOrBlank()) text else "$text\n${message.detail}")
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.openBook.collect { onOpenBook(it) }
    }
    BackHandler(enabled = openFeed != null) { viewModel.showFeed(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.web_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (openFeed != null) viewModel.showFeed(null) else onBack() }) {
                        Icon(FolioIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (openFeed != null) {
                        IconButton(onClick = viewModel::refreshFeed, enabled = busy == null) {
                            Icon(FolioIcons.Refresh, contentDescription = stringResource(R.string.web_refresh))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!enabled) {
                EmptyState(
                    title = stringResource(R.string.web_off_title),
                    message = stringResource(R.string.web_off_message),
                    icon = FolioIcons.Public,
                    actionLabel = stringResource(R.string.web_turn_on),
                    onAction = viewModel::enable,
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            TabRow(selectedTabIndex = tab) {
                listOf(R.string.web_tab_pages, R.string.web_tab_feeds, R.string.web_tab_novels).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }
            busy?.let { BusyLine(it) }
            when (tab) {
                0 -> PagesTab(initialUrl = shared.orEmpty(), busy = busy != null, onSave = viewModel::savePage)
                1 -> FeedsTab(viewModel, busy = busy != null)
                else -> NovelsTab(viewModel, busy = busy != null)
            }
        }
    }
}

@Composable
private fun BusyLine(busy: WebBusy) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        val label = stringResource(busy.label)
        Text(
            if (busy.count > 0) "$label (${busy.count})" else label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AddressField(
    initial: String,
    label: String,
    action: String,
    busy: Boolean,
    onSubmit: (String) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    val submit = {
        if (value.isNotBlank() && !busy) {
            onSubmit(value.trim())
        }
    }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(label) },
            singleLine = true,
            leadingIcon = { Icon(FolioIcons.Link, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = submit, enabled = value.isNotBlank() && !busy, modifier = Modifier.align(Alignment.End)) {
            Text(action)
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun PagesTab(initialUrl: String, busy: Boolean, onSave: (String) -> Unit) {
    val spacing = LocalSpacing.current
    Column(Modifier.padding(horizontal = spacing.screenPadding, vertical = 16.dp)) {
        AddressField(
            initial = initialUrl,
            label = stringResource(R.string.web_page_address),
            action = stringResource(R.string.web_save_page),
            busy = busy,
            onSubmit = onSave,
        )
        Hint(stringResource(R.string.web_pages_hint))
    }
}

@Composable
private fun FeedsTab(viewModel: WebViewModel, busy: Boolean) {
    val feeds by viewModel.feeds.collectAsStateWithLifecycle()
    val openFeed by viewModel.openFeed.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    openFeed?.let { feed ->
        FeedItems(feed, viewModel, busy)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = spacing.screenPadding, end = spacing.screenPadding, top = 16.dp, bottom = 80.dp),
    ) {
        item {
            AddressField(
                initial = "",
                label = stringResource(R.string.web_feed_address),
                action = stringResource(R.string.web_add_feed),
                busy = busy,
                onSubmit = viewModel::addFeed,
            )
            Hint(stringResource(R.string.web_feeds_hint))
        }
        if (feeds.isEmpty()) {
            item { EmptyState(stringResource(R.string.web_no_feeds), icon = FolioIcons.RssFeed) }
        }
        items(feeds, key = { it.url }) { feed ->
            ListCard(
                title = feed.title,
                subtitle = feed.siteUrl ?: feed.url,
                onClick = { viewModel.showFeed(feed) },
            ) {
                IconButton(onClick = { viewModel.removeFeed(feed) }) {
                    Icon(FolioIcons.Delete, contentDescription = stringResource(R.string.web_remove_feed))
                }
            }
        }
    }
}

@Composable
private fun FeedItems(feed: SavedFeed, viewModel: WebViewModel, busy: Boolean) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    LazyColumn(
        contentPadding = PaddingValues(start = spacing.screenPadding, end = spacing.screenPadding, top = 12.dp, bottom = 80.dp),
    ) {
        item {
            Text(feed.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
        }
        if (items.isEmpty() && !busy) {
            item { EmptyState(stringResource(R.string.web_feed_empty)) }
        }
        items(items, key = { it.id }) { item ->
            FeedItemCard(item, isSaved = item.link in saved, enabled = !busy, onOpen = { viewModel.openItem(item) })
        }
    }
}

@Composable
private fun FeedItemCard(item: FeedItem, isSaved: Boolean, enabled: Boolean, onOpen: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(item.author, item.published?.let { Format.relativeDate(it) }).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item.summary?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpen, enabled = enabled) {
                    Text(stringResource(if (isSaved) R.string.action_open else R.string.web_save_and_read))
                }
            }
        }
    }
}

@Composable
private fun NovelsTab(viewModel: WebViewModel, busy: Boolean) {
    val novels by viewModel.novels.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    LazyColumn(
        contentPadding = PaddingValues(start = spacing.screenPadding, end = spacing.screenPadding, top = 16.dp, bottom = 80.dp),
    ) {
        item {
            AddressField(
                initial = "",
                label = stringResource(R.string.web_novel_address),
                action = stringResource(R.string.web_follow_novel),
                busy = busy,
                onSubmit = viewModel::startNovel,
            )
            Hint(stringResource(R.string.web_novels_hint))
        }
        if (novels.isEmpty()) {
            item { EmptyState(stringResource(R.string.web_no_novels), icon = FolioIcons.MenuBook) }
        }
        items(novels, key = { it.bookId }) { novel ->
            NovelCard(novel, enabled = !busy, viewModel)
        }
    }
}

@Composable
private fun NovelCard(novel: WebNovel, enabled: Boolean, viewModel: WebViewModel) {
    val checked = if (novel.checkedAt > 0) Format.relativeDate(novel.checkedAt) else null
    ListCard(
        title = novel.title,
        subtitle = listOfNotNull(
            pluralStringResource(R.plurals.web_novel_chapters_plural, novel.chapters, novel.chapters),
            checked?.let { stringResource(R.string.web_novel_checked, it) },
        ).joinToString(" · "),
        onClick = { viewModel.openNovel(novel) },
    ) {
        IconButton(onClick = { viewModel.updateNovel(novel) }, enabled = enabled) {
            Icon(FolioIcons.Refresh, contentDescription = stringResource(R.string.web_novel_check))
        }
        IconButton(onClick = { viewModel.forgetNovel(novel) }) {
            Icon(FolioIcons.Close, contentDescription = stringResource(R.string.web_novel_forget))
        }
    }
}

@Composable
private fun ListCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions()
        }
    }
}
