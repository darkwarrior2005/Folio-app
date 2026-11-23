package app.folio.ui.screens.manga

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.MangaFilter
import app.folio.core.model.MangaSort
import app.folio.data.db.MangaWithCounts
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.EmptyState
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons

/**
 * The manga library's content, hosted as a tab inside [app.folio.ui.screens.library.LibraryScreen]
 * rather than as its own screen — manga lives alongside books, not off in its own corner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MangaLibraryTabContent(
    onOpenManga: (Long) -> Unit,
    onBrowse: () -> Unit,
    onEnableSources: () -> Unit,
    onOpenSourceWebView: (sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MangaLibraryViewModel = folioViewModel { MangaLibraryViewModel(it) },
) {
    val manga by viewModel.manga.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val sourcesEnabled by viewModel.sourcesEnabled.collectAsStateWithLifecycle()
    val sourceSearch by viewModel.sourceSearch.collectAsStateWithLifecycle()

    // Back from a source's site, where the user may have passed its check: search those again.
    var openedSiteOf by remember { mutableStateOf<Long?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        openedSiteOf?.let { sourceId ->
            openedSiteOf = null
            viewModel.retrySource(sourceId)
        }
    }
    val searchableSources by viewModel.searchableSources.collectAsStateWithLifecycle()
    val hiddenLanguages by viewModel.hiddenLanguages.collectAsStateWithLifecycle()
    val languageChoices by viewModel.languageChoices.collectAsStateWithLifecycle()
    var choosingLanguages by remember { mutableStateOf(false) }

    var pendingRemoval by remember { mutableStateOf<MangaWithCounts?>(null) }
    var choosingSources by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val canSearchSources = sourcesEnabled && query.isNotBlank()

    fun searchSources() {
        focusManager.clearFocus()
        viewModel.searchSources()
    }

    // Back closes a search of the sources before it leaves the library.
    BackHandler(enabled = sourceSearch != null) { viewModel.setQuery("") }

    Column(modifier.fillMaxSize()) {
        if (!sourcesEnabled) {
            SourcesOffNotice(onEnableSources)
        }

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = {
                Text(
                    stringResource(
                        if (sourcesEnabled) R.string.manga_search_library_hint else R.string.manga_search_library,
                    ),
                )
            },
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) {
                        Icon(FolioIcons.Close, contentDescription = stringResource(R.string.action_clear_search))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = if (canSearchSources) ImeAction.Search else ImeAction.Done),
            keyboardActions = KeyboardActions(
                onSearch = { searchSources() },
                onDone = { focusManager.clearFocus() },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (canSearchSources) {
            val included = searchableSources.count { it.included }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { searchSources() },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(FolioIcons.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = pluralStringResource(R.plurals.manga_search_sources_count, included, included),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                TextButton(onClick = { choosingSources = true }) {
                    Text(stringResource(R.string.manga_choose_sources))
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MangaFilter.entries.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { viewModel.setFilter(option) },
                    label = { Text(stringResource(option.labelRes())) },
                )
            }
        }

        val search = sourceSearch
        if (search != null) {
            SourceSearchResults(
                search = search,
                library = manga,
                onOpenLibraryManga = onOpenManga,
                onOpenSourceManga = { sourceId, result -> viewModel.openSourceManga(sourceId, result, onOpenManga) },
                onChooseSources = { choosingSources = true },
                onOpenSourceWebView = {
                    openedSiteOf = it
                    onOpenSourceWebView(it)
                },
                onRetrySource = viewModel::retrySource,
                modifier = Modifier.weight(1f),
            )
        } else {
            MangaLibraryGrid(
                manga = manga,
                onBrowse = onBrowse,
                onOpenManga = onOpenManga,
                onRemove = { pendingRemoval = it },
            )
        }
    }

    if (choosingSources) {
        SourceChooserSheet(
            sources = searchableSources,
            onSetIncluded = viewModel::setSourcesIncluded,
            onChooseLanguages = {
                choosingSources = false
                choosingLanguages = true
            },
            onDismiss = { choosingSources = false },
        )
    }

    if (choosingLanguages) {
        LanguagePickerSheet(
            choices = languageChoices,
            hidden = hiddenLanguages,
            onSetHidden = viewModel::setHiddenLanguages,
            onDismiss = { choosingLanguages = false },
        )
    }

    pendingRemoval?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.manga_remove_title),
            message = stringResource(R.string.manga_remove_message, entry.manga.title),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                viewModel.remove(entry.manga.id)
                pendingRemoval = null
            },
            onDismiss = { pendingRemoval = null },
        )
    }
}

@Composable
private fun MangaLibraryGrid(
    manga: List<MangaWithCounts>,
    onBrowse: () -> Unit,
    onOpenManga: (Long) -> Unit,
    onRemove: (MangaWithCounts) -> Unit,
) {
    if (manga.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.manga_empty_title),
            message = stringResource(R.string.manga_empty_message),
            icon = FolioIcons.Explore,
            actionLabel = stringResource(R.string.manga_browse),
            onAction = onBrowse,
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 118.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(manga, key = { it.manga.id }) { entry ->
                MangaGridCard(
                    entry = entry,
                    onClick = { onOpenManga(entry.manga.id) },
                    onLongClick = { onRemove(entry) },
                )
            }
        }
    }
}

@Composable
private fun SourcesOffNotice(onEnable: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onEnable),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.manga_sources_off_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.manga_sources_off_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Unread/downloaded counts show as text, not as color-only badges — a card that's otherwise
 * identical shouldn't rely on hue alone to say "this one needs attention".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MangaGridCard(
    entry: MangaWithCounts,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO),
        ) {
            MangaCover(
                url = entry.manga.thumbnailUrl,
                localPath = entry.manga.coverPath,
                contentDescription = entry.manga.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sourceId = entry.manga.sourceId,
            )
        }
        Text(
            text = entry.manga.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        val subtitle = buildList {
            if (entry.unreadCount > 0) {
                add(pluralStringResource(R.plurals.manga_unread_count, entry.unreadCount, entry.unreadCount))
            }
            if (entry.downloadedCount > 0) {
                add(pluralStringResource(R.plurals.manga_downloaded_count, entry.downloadedCount, entry.downloadedCount))
            }
        }.joinToString(" · ")
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 1.dp),
            )
        }
    }
}

internal const val COVER_RATIO = 0.68f

fun MangaSort.labelRes(): Int = when (this) {
    MangaSort.TITLE -> R.string.manga_sort_title
    MangaSort.LAST_READ -> R.string.manga_sort_last_read
    MangaSort.DATE_ADDED -> R.string.manga_sort_date_added
    MangaSort.UNREAD_COUNT -> R.string.manga_sort_unread
    MangaSort.LAST_UPDATED -> R.string.manga_sort_updated
}

private fun MangaFilter.labelRes(): Int = when (this) {
    MangaFilter.ALL -> R.string.manga_filter_all
    MangaFilter.UNREAD -> R.string.manga_filter_unread
    MangaFilter.DOWNLOADED -> R.string.manga_filter_downloaded
    MangaFilter.STARTED -> R.string.manga_filter_started
}
