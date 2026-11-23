package app.folio.ui.screens.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.BookSort
import app.folio.core.model.LibraryBook
import app.folio.core.model.LibraryGrouping
import app.folio.core.model.LibraryLayout
import app.folio.core.model.MangaSort
import app.folio.core.model.SmartCollection
import app.folio.importer.DuplicateAction
import app.folio.ui.LocalContainer
import app.folio.ui.components.BookGridCard
import app.folio.ui.components.BookListRow
import app.folio.ui.components.EmptyState
import app.folio.ui.components.FolioChip
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons
import app.folio.ui.screens.manga.MangaLibraryTabContent
import app.folio.ui.screens.manga.MangaLibraryViewModel
import app.folio.ui.screens.manga.labelRes
import app.folio.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (Long) -> Unit,
    onBookDetails: (Long) -> Unit,
    onSearch: () -> Unit,
    onCollections: () -> Unit,
    onMusic: () -> Unit,
    onOpenManga: (Long) -> Unit,
    onBrowseManga: () -> Unit,
    onFromWeb: () -> Unit,
    onMangaExtensions: () -> Unit,
    onMangaDownloads: () -> Unit,
    onEnableMangaSources: () -> Unit,
    onOpenSourceWebView: (sourceId: Long) -> Unit,
    viewModel: LibraryViewModel = folioViewModel { LibraryViewModel(it) },
    mangaViewModel: MangaLibraryViewModel = folioViewModel { MangaLibraryViewModel(it) },
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val grouped by viewModel.grouped.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val duplicate by viewModel.duplicate.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    val mangaSort by mangaViewModel.sort.collectAsStateWithLifecycle()
    val mangaQueueSize by mangaViewModel.queueSize.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }

    // A search handed over from elsewhere (a manga with no chapters in its own source).
    val container = LocalContainer.current
    val pendingSearch by container.pendingMangaSearch.collectAsStateWithLifecycle()
    LaunchedEffect(pendingSearch) {
        val title = pendingSearch ?: return@LaunchedEffect
        container.pendingMangaSearch.value = null
        tab = 1
        mangaViewModel.setQuery(title)
        mangaViewModel.searchSources()
    }
    var showFilters by remember { mutableStateOf(false) }
    var showDisplayOptions by remember { mutableStateOf(false) }
    var showMangaSortMenu by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<LibraryBook?>(null) }
    var showBulkSheet by remember { mutableStateOf(false) }

    val openFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.import(uris.map { it.toString() })
    }
    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.importFolder(it.toString()) }
    }

    val selectionMode = tab == 0 && selection.isNotEmpty()

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.library_selected_count, selection.size)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(FolioIcons.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        TextButton(onClick = viewModel::selectAll) {
                            Text(stringResource(R.string.action_select_all))
                        }
                        TextButton(onClick = { showBulkSheet = true }) {
                            Text(stringResource(R.string.action_more))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    actions = {
                        IconButton(onClick = onMusic) {
                            Icon(FolioIcons.LibraryMusic, contentDescription = stringResource(R.string.music_title))
                        }
                        if (tab == 0) {
                            IconButton(onClick = onSearch) {
                                Icon(FolioIcons.Search, contentDescription = stringResource(R.string.action_search))
                            }
                            IconButton(onClick = { showFilters = true }) {
                                Icon(
                                    FolioIcons.FilterList,
                                    contentDescription = stringResource(R.string.library_filter),
                                    tint = if (filter.activeCount > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            IconButton(onClick = { showDisplayOptions = true }) {
                                Icon(
                                    FolioIcons.Tune,
                                    contentDescription = stringResource(R.string.library_display_options),
                                )
                            }
                        } else {
                            IconButton(onClick = onMangaDownloads) {
                                BadgedBox(badge = { if (mangaQueueSize > 0) Badge { Text("$mangaQueueSize") } }) {
                                    Icon(FolioIcons.Download, contentDescription = stringResource(R.string.manga_downloads))
                                }
                            }
                            IconButton(onClick = onMangaExtensions) {
                                Icon(FolioIcons.Extension, contentDescription = stringResource(R.string.manga_extensions))
                            }
                            Box {
                                IconButton(onClick = { showMangaSortMenu = true }) {
                                    Icon(FolioIcons.Sort, contentDescription = stringResource(R.string.library_sort))
                                }
                                DropdownMenu(
                                    expanded = showMangaSortMenu,
                                    onDismissRequest = { showMangaSortMenu = false },
                                ) {
                                    MangaSort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(stringResource(option.labelRes())) },
                                            onClick = {
                                                mangaViewModel.setSort(option)
                                                showMangaSortMenu = false
                                            },
                                            trailingIcon = {
                                                if (option == mangaSort) {
                                                    Icon(FolioIcons.MenuBook, contentDescription = null)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selectionMode) {
                Box {
                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = true },
                        icon = { Icon(FolioIcons.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.action_import)) },
                    )
                    DropdownMenu(expanded = showFabMenu, onDismissRequest = { showFabMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_import)) },
                            onClick = {
                                showFabMenu = false
                                openFiles.launch(arrayOf("*/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_import_folder)) },
                            leadingIcon = { Icon(FolioIcons.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                openFolder.launch(null)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.web_open_from_library)) },
                            leadingIcon = { Icon(FolioIcons.Public, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                onFromWeb()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manga_find_online)) },
                            leadingIcon = { Icon(FolioIcons.Explore, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                onBrowseManga()
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!selectionMode) {
                TabRow(selectedTabIndex = tab) {
                    listOf(R.string.library_tab_books, R.string.library_tab_manga).forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                    }
                }
            }

            if (tab == 0) {
                if (importProgress.running) {
                    Column(Modifier.padding(horizontal = spacing.screenPadding, vertical = 8.dp)) {
                        Text(
                            text = stringResource(
                                R.string.import_progress,
                                importProgress.completed + 1,
                                importProgress.total,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { importProgress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                SmartCollectionRow(
                    selected = filter.smart,
                    onSelect = viewModel::setSmart,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                if (filter.activeCount > 0) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenPadding),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pluralStringResource(R.plurals.library_books_count, books.size, books.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = viewModel::clearFilters) {
                            Text(stringResource(R.string.filter_clear))
                        }
                    }
                }

                when {
                    books.isEmpty() && filter.isActive -> EmptyState(
                        title = stringResource(R.string.empty_filter),
                        actionLabel = stringResource(R.string.filter_clear),
                        onAction = viewModel::clearFilters,
                        modifier = Modifier.fillMaxSize(),
                    )

                    books.isEmpty() -> EmptyState(
                        title = stringResource(R.string.empty_library_title),
                        message = stringResource(R.string.empty_library_message),
                        icon = FolioIcons.Book,
                        actionLabel = stringResource(R.string.action_import),
                        onAction = { openFiles.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> LibraryContent(
                        grouped = grouped,
                        settings = settings.library,
                        selection = selection,
                        selectionMode = selectionMode,
                        contentPadding = PaddingValues(
                            start = spacing.screenPadding,
                            end = spacing.screenPadding,
                            bottom = 96.dp,
                        ),
                        onClick = { book ->
                            if (selectionMode) viewModel.toggleSelected(book.id) else onOpenBook(book.id)
                        },
                        onLongClick = { book ->
                            if (selectionMode) viewModel.toggleSelected(book.id) else actionTarget = book
                        },
                    )
                }
            } else {
                MangaLibraryTabContent(
                    onOpenManga = onOpenManga,
                    onBrowse = onBrowseManga,
                    onEnableSources = onEnableMangaSources,
                    onOpenSourceWebView = onOpenSourceWebView,
                    modifier = Modifier.fillMaxSize(),
                    viewModel = mangaViewModel,
                )
            }
        }
    }

    if (showFilters) {
        LibraryFilterSheet(
            viewModel = viewModel,
            onDismiss = { showFilters = false },
            onOpenCollections = {
                showFilters = false
                onCollections()
            },
        )
    }

    if (showDisplayOptions) {
        DisplayOptionsSheet(
            viewModel = viewModel,
            onDismiss = { showDisplayOptions = false },
        )
    }

    actionTarget?.let { book ->
        BookActionSheet(
            book = book,
            viewModel = viewModel,
            onDismiss = { actionTarget = null },
            onOpen = {
                actionTarget = null
                onOpenBook(book.id)
            },
            onDetails = {
                actionTarget = null
                onBookDetails(book.id)
            },
        )
    }

    if (showBulkSheet) {
        BulkActionSheet(
            viewModel = viewModel,
            onDismiss = { showBulkSheet = false },
        )
    }

    duplicate?.let { pending ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.dialog_duplicate_title)) },
            text = {
                Text(stringResource(R.string.dialog_duplicate_message, pending.fileName, pending.existingTitle))
            },
            confirmButton = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.resolveDuplicate(DuplicateAction.OPEN_EXISTING)
                            onBookDetails(pending.existingBookId)
                        },
                    ) { Text(stringResource(R.string.dialog_duplicate_open)) }
                    TextButton(onClick = { viewModel.resolveDuplicate(DuplicateAction.IMPORT_SEPARATELY) }) {
                        Text(stringResource(R.string.dialog_duplicate_import))
                    }
                    TextButton(onClick = { viewModel.resolveDuplicate(DuplicateAction.REPLACE) }) {
                        Text(stringResource(R.string.dialog_duplicate_replace))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.resolveDuplicate(DuplicateAction.CANCEL) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    LaunchedEffect(selectionMode) {
        if (!selectionMode) showBulkSheet = false
    }
}

@Composable
private fun LibraryContent(
    grouped: List<Pair<String?, List<LibraryBook>>>,
    settings: app.folio.data.settings.LibrarySettings,
    selection: Set<Long>,
    selectionMode: Boolean,
    contentPadding: PaddingValues,
    onClick: (LibraryBook) -> Unit,
    onLongClick: (LibraryBook) -> Unit,
) {
    when (settings.layout) {
        LibraryLayout.LIST, LibraryLayout.DETAILED -> LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            grouped.forEach { (group, books) ->
                if (group != null) {
                    item(key = "header-$group") { GroupHeader(group) }
                }
                items(books, key = { it.id }) { book ->
                    BookListRow(
                        book = book,
                        settings = settings,
                        selected = book.id in selection,
                        selectionMode = selectionMode,
                        detailed = settings.layout == LibraryLayout.DETAILED,
                        onClick = { onClick(book) },
                        onLongClick = { onLongClick(book) },
                    )
                }
            }
        }

        else -> {
            val minSize = when (settings.layout) {
                LibraryLayout.COMPACT -> 92.dp
                LibraryLayout.COVER_ONLY -> 110.dp
                else -> 128.dp
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                grouped.forEach { (group, books) ->
                    if (group != null) {
                        item(key = "header-$group", span = { GridItemSpan(maxLineSpan) }) { GroupHeader(group) }
                    }
                    items(books, key = { it.id }) { book ->
                        BookGridCard(
                            book = book,
                            settings = if (settings.layout == LibraryLayout.COVER_ONLY) {
                                settings.copy(showTitles = false)
                            } else {
                                settings
                            },
                            selected = book.id in selection,
                            selectionMode = selectionMode,
                            compact = settings.layout == LibraryLayout.COMPACT,
                            onClick = { onClick(book) },
                            onLongClick = { onLongClick(book) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SmartCollectionRow(
    selected: SmartCollection?,
    onSelect: (SmartCollection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenPadding, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmartCollection.entries.forEach { smart ->
            FolioChip(
                label = smart.label(),
                selected = selected == smart,
                onClick = { onSelect(smart) },
            )
        }
    }
}

@Composable
fun SmartCollection.label(): String = stringResource(
    when (this) {
        SmartCollection.RECENTLY_ADDED -> R.string.smart_recently_added
        SmartCollection.RECENTLY_READ -> R.string.smart_recently_read
        SmartCollection.CURRENTLY_READING -> R.string.smart_currently_reading
        SmartCollection.FINISHED -> R.string.smart_finished
        SmartCollection.NEVER_OPENED -> R.string.smart_never_opened
        SmartCollection.FAVORITES -> R.string.smart_favorites
        SmartCollection.LONG_BOOKS -> R.string.smart_long_books
        SmartCollection.SHORT_READS -> R.string.smart_short_reads
        SmartCollection.UNSORTED -> R.string.smart_unsorted
    },
)

@Composable
fun BookSort.label(): String = stringResource(
    when (this) {
        BookSort.TITLE -> R.string.sort_title
        BookSort.AUTHOR -> R.string.sort_author
        BookSort.RECENTLY_OPENED -> R.string.sort_recently_opened
        BookSort.RECENTLY_ADDED -> R.string.sort_recently_added
        BookSort.PROGRESS -> R.string.sort_progress
        BookSort.FILE_SIZE -> R.string.sort_file_size
        BookSort.YEAR -> R.string.sort_year
        BookSort.SERIES -> R.string.sort_series
        BookSort.CUSTOM -> R.string.sort_custom
    },
)

@Composable
fun LibraryLayout.label(): String = stringResource(
    when (this) {
        LibraryLayout.GRID -> R.string.layout_grid
        LibraryLayout.LIST -> R.string.layout_list
        LibraryLayout.COMPACT -> R.string.layout_compact
        LibraryLayout.COVER_ONLY -> R.string.layout_cover_only
        LibraryLayout.DETAILED -> R.string.layout_detailed
    },
)

@Composable
fun LibraryGrouping.label(): String = stringResource(
    when (this) {
        LibraryGrouping.NONE -> R.string.group_none
        LibraryGrouping.CATEGORY -> R.string.group_category
        LibraryGrouping.SERIES -> R.string.group_series
        LibraryGrouping.AUTHOR -> R.string.group_author
        LibraryGrouping.STATUS -> R.string.group_status
        LibraryGrouping.FORMAT -> R.string.group_format
    },
)
