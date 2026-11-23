package app.folio.ui.screens.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.ChapterCondition
import app.folio.ui.components.SectionHeader
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons
import app.folio.ui.util.Format
import eu.kanade.tachiyomi.source.model.SManga

/** What the chapter-number dialog is for. */
private enum class RangePurpose { SELECT, DOWNLOAD }

/**
 * One manga: what it is, and its chapters.
 *
 * Long-pressing a chapter starts selecting; long-pressing another then takes in every chapter
 * between the two. Selected chapters can be downloaded, deleted, marked or bookmarked together,
 * and the download menu fetches by rule (the next few unread, everything unread, a number range)
 * without picking chapters one by one.
 */
@Composable
fun MangaDetailsScreen(
    mangaId: Long,
    onBack: () -> Unit,
    onReadChapter: (Long) -> Unit,
    onOpenWebView: (sourceId: Long) -> Unit,
    onOpenSourceSettings: (sourceId: Long) -> Unit,
    onSearchOtherSources: (title: String) -> Unit,
    viewModel: MangaDetailsViewModel = folioViewModel(key = "manga-$mangaId") {
        MangaDetailsViewModel(it, mangaId)
    },
) {
    val manga by viewModel.manga.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val refreshError by viewModel.refreshError.collectAsStateWithLifecycle()

    // Back from passing a site's check: try again straight away.
    var openedSite by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (openedSite) {
            openedSite = false
            if (refreshError != null) viewModel.refresh()
        }
    }

    val snackbar = remember { SnackbarHostState() }
    var descriptionExpanded by remember { mutableStateOf(false) }
    var rangePurpose by remember { mutableStateOf<RangePurpose?>(null) }
    val selecting = selection.isNotEmpty()

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(
                    count = selection.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onInvert = viewModel::invertSelection,
                    onSelectWhere = viewModel::selectWhere,
                    onSelectRange = { rangePurpose = RangePurpose.SELECT },
                )
            } else {
                TopAppBar(
                    title = { Text(manga?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(FolioIcons.ArrowBack, stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (manga?.favorite == true) {
                                    FolioIcons.Favorite
                                } else {
                                    FolioIcons.FavoriteBorder
                                },
                                contentDescription = stringResource(R.string.manga_favorite),
                            )
                        }
                        if (chapters.isNotEmpty()) {
                            DownloadMenu(
                                onNextUnread = viewModel::downloadNextUnread,
                                onAllUnread = { viewModel.downloadWhere(ChapterCondition.UNREAD) },
                                onAll = viewModel::downloadAll,
                                onRange = { rangePurpose = RangePurpose.DOWNLOAD },
                            )
                        }
                        IconButton(onClick = viewModel::refresh, enabled = !refreshing) {
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.padding(4.dp))
                            } else {
                                Icon(FolioIcons.Refresh, stringResource(R.string.action_refresh))
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionBottomBar(
                    onDownload = viewModel::downloadSelected,
                    onDeleteDownloads = viewModel::deleteSelectedDownloads,
                    onMarkRead = { viewModel.markSelectedRead(true) },
                    onMarkUnread = { viewModel.markSelectedRead(false) },
                    onBookmark = { viewModel.bookmarkSelected(true) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (chapters.isNotEmpty() && !selecting) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.nextChapterId()?.let(onReadChapter) },
                    icon = { Icon(FolioIcons.MenuBook, contentDescription = null) },
                    text = { Text(stringResource(R.string.manga_read)) },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
                Row(Modifier.padding(16.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier
                            .width(120.dp)
                            .aspectRatio(COVER_RATIO),
                    ) {
                        MangaCover(
                            url = manga?.thumbnailUrl,
                            localPath = manga?.coverPath,
                            contentDescription = manga?.title,
                            modifier = Modifier.fillMaxSize(),
                            sourceId = manga?.sourceId,
                        )
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(
                            text = manga?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        manga?.author?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        manga?.artist?.takeIf { it != manga?.author }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = stringResource(statusLabel(manga?.status ?: SManga.UNKNOWN)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        manga?.lastChapterFetchAt?.let { fetched ->
                            Text(
                                text = stringResource(R.string.manga_checked, Format.date(fetched)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            manga?.description?.takeIf { it.isNotBlank() }?.let { description ->
                item {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (descriptionExpanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                            .clickable { descriptionExpanded = !descriptionExpanded },
                    )
                }
            }

            item {
                SectionHeader(
                    title = pluralStringResource(R.plurals.manga_chapter_count, chapters.size, chapters.size),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            refreshError?.let { error ->
                item(key = "refresh-error") {
                    RefreshErrorCard(
                        error = error,
                        onRetry = viewModel::refresh,
                        onOpenSite = manga?.sourceId?.let { sourceId ->
                            {
                                openedSite = true
                                onOpenWebView(sourceId)
                            }
                        },
                        onOpenSettings = manga?.sourceId?.takeIf { error.sourceHasSettings }?.let { sourceId ->
                            {
                                // Coming back refreshes, so a changed setting shows straight away.
                                openedSite = true
                                onOpenSourceSettings(sourceId)
                            }
                        },
                        onSearchElsewhere = manga?.title?.takeIf { error.noChapters }?.let { title ->
                            { onSearchOtherSources(title) }
                        },
                    )
                }
            }

            if (chapters.isEmpty() && refreshing) {
                item {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.manga_fetching_chapters),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            } else if (chapters.isEmpty() && refreshError == null) {
                item {
                    Text(
                        text = stringResource(R.string.manga_no_chapters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            items(chapters, key = { it.chapter.id }) { row ->
                val id = row.chapter.id
                ChapterRowItem(
                    row = row,
                    selecting = selecting,
                    selected = id in selection,
                    onClick = { if (selecting) viewModel.toggleSelection(id) else onReadChapter(id) },
                    onLongClick = { if (selecting) viewModel.selectRangeTo(id) else viewModel.toggleSelection(id) },
                    onDownload = { viewModel.download(listOf(id)) },
                    onDeleteDownload = { viewModel.deleteDownloads(listOf(id)) },
                    onToggleRead = { viewModel.setRead(listOf(id), !row.chapter.read) },
                    onToggleBookmark = { viewModel.toggleBookmark(row.chapter) },
                )
            }
        }
    }

    rangePurpose?.let { purpose ->
        ChapterRangeDialog(
            title = stringResource(
                if (purpose == RangePurpose.DOWNLOAD) R.string.manga_download_range else R.string.manga_select_range,
            ),
            onConfirm = { from, to ->
                if (purpose == RangePurpose.DOWNLOAD) viewModel.downloadNumbered(from, to) else viewModel.selectNumbered(from, to)
                rangePurpose = null
            },
            onDismiss = { rangePurpose = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RefreshErrorCard(
    error: RefreshError,
    onRetry: () -> Unit,
    onOpenSite: (() -> Unit)?,
    onOpenSettings: (() -> Unit)?,
    onSearchElsewhere: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(if (error.noChapters) R.string.manga_no_chapters_found else R.string.manga_refresh_failed),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (error.noChapters) {
                Text(
                    text = stringResource(
                        if (onOpenSettings != null) R.string.manga_no_chapters_hint_settings else R.string.manga_no_chapters_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            FlowRow(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onSearchElsewhere != null) {
                    Button(onClick = onSearchElsewhere) { Text(stringResource(R.string.manga_search_other_sources)) }
                }
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                if (onOpenSettings != null) {
                    OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.manga_source_settings)) }
                }
                if (onOpenSite != null) {
                    OutlinedButton(onClick = onOpenSite) { Text(stringResource(R.string.manga_open_site)) }
                }
            }
        }
    }
}

@Composable
private fun DownloadMenu(
    onNextUnread: (Int) -> Unit,
    onAllUnread: () -> Unit,
    onAll: () -> Unit,
    onRange: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(FolioIcons.DownloadForOffline, stringResource(R.string.manga_download_menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun pick(action: () -> Unit) {
                open = false
                action()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manga_download_next_one)) },
                onClick = { pick { onNextUnread(1) } },
            )
            listOf(5, 10, 25).forEach { count ->
                DropdownMenuItem(
                    text = { Text(pluralStringResource(R.plurals.manga_download_next_unread, count, count)) },
                    onClick = { pick { onNextUnread(count) } },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manga_download_all_unread)) },
                onClick = { pick(onAllUnread) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manga_download_all)) },
                onClick = { pick(onAll) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.manga_download_range_menu)) },
                onClick = { pick(onRange) },
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onSelectWhere: (ChapterCondition) -> Unit,
    onSelectRange: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.manga_selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(FolioIcons.Close, stringResource(R.string.manga_selection_done))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(FolioIcons.SelectAll, stringResource(R.string.manga_select_all))
            }
            IconButton(onClick = onInvert) {
                Icon(FolioIcons.FlipToBack, stringResource(R.string.manga_select_invert))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(FolioIcons.MoreVert, stringResource(R.string.manga_select_by))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    listOf(
                        ChapterCondition.UNREAD to R.string.manga_select_unread,
                        ChapterCondition.READ to R.string.manga_select_read,
                        ChapterCondition.NOT_DOWNLOADED to R.string.manga_select_not_downloaded,
                        ChapterCondition.DOWNLOADED to R.string.manga_select_downloaded,
                        ChapterCondition.BOOKMARKED to R.string.manga_select_bookmarked,
                    ).forEach { (condition, label) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(label)) },
                            onClick = {
                                menuOpen = false
                                onSelectWhere(condition)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.manga_select_range_menu)) },
                        onClick = {
                            menuOpen = false
                            onSelectRange()
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun SelectionBottomBar(
    onDownload: () -> Unit,
    onDeleteDownloads: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onBookmark: () -> Unit,
) {
    BottomAppBar {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onDownload) {
                Icon(FolioIcons.Download, stringResource(R.string.manga_download))
            }
            IconButton(onClick = onDeleteDownloads) {
                Icon(FolioIcons.Delete, stringResource(R.string.manga_delete_download))
            }
            IconButton(onClick = onMarkRead) {
                Icon(FolioIcons.DoneAll, stringResource(R.string.manga_mark_read))
            }
            IconButton(onClick = onMarkUnread) {
                Icon(FolioIcons.RemoveDone, stringResource(R.string.manga_mark_unread))
            }
            IconButton(onClick = onBookmark) {
                Icon(FolioIcons.Bookmark, stringResource(R.string.manga_bookmark))
            }
        }
    }
}

/** Asks for a chapter-number range, e.g. 12 to 30; either end may come first. */
@Composable
private fun ChapterRangeDialog(
    title: String,
    onConfirm: (from: Float, to: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    val fromValue = from.trim().replace(',', '.').toFloatOrNull()
    val toValue = to.trim().replace(',', '.').toFloatOrNull()
    val valid = fromValue != null && toValue != null && fromValue >= 0f && toValue >= 0f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.manga_range_message), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = from,
                        onValueChange = { from = it },
                        label = { Text(stringResource(R.string.manga_range_from)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = to,
                        onValueChange = { to = it },
                        label = { Text(stringResource(R.string.manga_range_to)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(fromValue ?: 0f, toValue ?: 0f) }, enabled = valid) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChapterRowItem(
    row: ChapterRow,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    val chapter = row.chapter
    ListItem(
        headlineContent = {
            Text(
                text = chapter.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (chapter.read) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        supportingContent = {
            val parts = buildList {
                chapter.scanlator?.takeIf { it.isNotBlank() }?.let(::add)
                if (chapter.dateUpload > 0) add(Format.date(chapter.dateUpload))
                if (!chapter.read && chapter.lastPageRead > 0) {
                    add("page ${chapter.lastPageRead + 1}")
                }
                if (row.downloaded) add(stringResource(R.string.manga_downloaded))
            }
            if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
        },
        leadingContent = {
            if (selecting) {
                // The whole row toggles; the box only shows the state.
                Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.padding(12.dp))
            } else {
                IconButton(onClick = onToggleRead) {
                    Icon(
                        // Shape, not just tint, carries read/unread -- color alone isn't a reliable signal.
                        imageVector = if (chapter.read) FolioIcons.CheckCircle else FolioIcons.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.manga_mark_read),
                        tint = if (chapter.read) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        },
        trailingContent = {
            if (!selecting) {
                Row {
                    IconButton(onClick = onToggleBookmark) {
                        Icon(
                            imageVector = if (chapter.bookmark) {
                                FolioIcons.Bookmark
                            } else {
                                FolioIcons.BookmarkBorder
                            },
                            contentDescription = stringResource(R.string.manga_bookmark),
                        )
                    }
                    when {
                        row.downloading -> Box(Modifier.padding(12.dp)) {
                            CircularProgressIndicator(Modifier.padding(2.dp))
                        }

                        row.downloaded -> IconButton(onClick = onDeleteDownload) {
                            Icon(FolioIcons.DownloadDone, stringResource(R.string.manga_delete_download))
                        }

                        else -> IconButton(onClick = onDownload) {
                            Icon(FolioIcons.Download, stringResource(R.string.manga_download))
                        }
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        modifier = Modifier
            .semantics { this.selected = selected }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(
                    if (selecting) R.string.manga_select_range_to_here else R.string.manga_select_chapter,
                ),
            ),
    )
}

private fun statusLabel(status: Int): Int = when (status) {
    SManga.ONGOING -> R.string.manga_status_ongoing
    SManga.COMPLETED -> R.string.manga_status_completed
    SManga.LICENSED -> R.string.manga_status_licensed
    SManga.PUBLISHING_FINISHED -> R.string.manga_status_publishing_finished
    SManga.CANCELLED -> R.string.manga_status_cancelled
    SManga.ON_HIATUS -> R.string.manga_status_hiatus
    else -> R.string.manga_status_unknown
}
