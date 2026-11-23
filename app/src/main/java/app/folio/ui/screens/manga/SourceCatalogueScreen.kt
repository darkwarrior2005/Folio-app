package app.folio.ui.screens.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.ui.components.EmptyState
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons
import eu.kanade.tachiyomi.source.model.SManga

/** One source's catalogue: what is popular, what is new, and a search of it. */
@Composable
fun SourceCatalogueScreen(
    sourceId: Long,
    onBack: () -> Unit,
    onOpenManga: (Long) -> Unit,
    onOpenWebView: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SourceCatalogueViewModel = folioViewModel(key = "catalogue-$sourceId") {
        SourceCatalogueViewModel(it, sourceId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    // Back from the site, where the user may have passed its check: load again if it had failed.
    var openedSite by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (openedSite) {
            openedSite = false
            if (state.error != null) viewModel.retry()
        }
    }
    val openSite = {
        openedSite = true
        onOpenWebView()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.sourceName.ifBlank { stringResource(R.string.manga_browse) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FolioIcons.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = openSite) {
                        Icon(FolioIcons.Public, stringResource(R.string.manga_open_site))
                    }
                    if (viewModel.hasSettings) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(FolioIcons.Settings, stringResource(R.string.manga_source_settings))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.manga_search_source)) },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = viewModel::search) {
                        Icon(FolioIcons.Search, stringResource(R.string.manga_search_source))
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { viewModel.search() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.mode == CatalogueMode.POPULAR,
                    onClick = { viewModel.load(CatalogueMode.POPULAR) },
                    label = { Text(stringResource(R.string.manga_popular)) },
                )
                if (state.supportsLatest) {
                    FilterChip(
                        selected = state.mode == CatalogueMode.LATEST,
                        onClick = { viewModel.load(CatalogueMode.LATEST) },
                        label = { Text(stringResource(R.string.manga_latest)) },
                    )
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.items.isEmpty() && state.error != null -> EmptyState(
                    title = stringResource(R.string.manga_source_error_title),
                    message = state.error,
                    icon = FolioIcons.CloudOff,
                    actionLabel = stringResource(
                        if (state.needsWebView) R.string.manga_open_site else R.string.action_retry,
                    ),
                    onAction = if (state.needsWebView) openSite else viewModel::retry,
                )

                state.items.isEmpty() -> EmptyState(
                    title = stringResource(R.string.manga_no_results_title),
                    message = stringResource(R.string.manga_no_results_message),
                    icon = FolioIcons.Search,
                )

                else -> CatalogueGrid(
                    sourceId = sourceId,
                    items = state.items,
                    loadingMore = state.loadingMore,
                    hasNextPage = state.hasNextPage,
                    onLoadMore = viewModel::loadMore,
                    onOpen = { manga -> viewModel.openManga(manga, onOpenManga) },
                )
            }
        }
    }
}

@Composable
private fun CatalogueGrid(
    sourceId: Long,
    items: List<SManga>,
    loadingMore: Boolean,
    hasNextPage: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (SManga) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(items, key = { index, manga -> "$index-${manga.url}" }) { index, manga ->
            // Fetching the next page as the end comes into view, rather than on a button.
            if (index == items.lastIndex && hasNextPage) {
                LaunchedEffect(items.size) { onLoadMore() }
            }
            CatalogueCard(manga = manga, sourceId = sourceId, onClick = { onOpen(manga) })
        }

        if (loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun CatalogueCard(manga: SManga, sourceId: Long, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_RATIO),
        ) {
            MangaCover(
                url = manga.thumbnail_url,
                localPath = null,
                contentDescription = manga.title,
                modifier = Modifier.fillMaxSize(),
                sourceId = sourceId,
            )
        }
        Text(
            text = manga.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}
