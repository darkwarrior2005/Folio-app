package app.folio.ui.screens.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.data.db.MangaWithCounts
import app.folio.manga.source.SourceSearchResult
import app.folio.manga.source.SourceSearchState
import app.folio.ui.components.EmptyState
import app.folio.ui.components.SectionHeader
import app.folio.ui.icons.FolioIcons
import eu.kanade.tachiyomi.source.model.SManga
import java.util.Locale

private val CARD_WIDTH = 110.dp

/**
 * A search across the user's library and every installed source they have not turned off.
 *
 * Sources with results come first, then those still searching, then those that failed; sources
 * that found nothing are counted in one line rather than each taking a row.
 */
@Composable
internal fun SourceSearchResults(
    search: SourceSearch,
    library: List<MangaWithCounts>,
    onOpenLibraryManga: (Long) -> Unit,
    onOpenSourceManga: (sourceId: Long, manga: SManga) -> Unit,
    onChooseSources: () -> Unit,
    onOpenSourceWebView: (sourceId: Long) -> Unit,
    onRetrySource: (sourceId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val withResults = search.results.filter { (it.state as? SourceSearchState.Done)?.manga?.isNotEmpty() == true }
    val searching = search.results.filter { it.state is SourceSearchState.Loading }
    val failed = search.results.filter { it.state is SourceSearchState.Failed }
    val foundNothing = search.results.count { (it.state as? SourceSearchState.Done)?.manga?.isEmpty() == true }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        if (library.isNotEmpty()) {
            item(key = "library-header") {
                SectionHeader(stringResource(R.string.manga_search_in_library), Modifier.padding(horizontal = 16.dp))
            }
            item(key = "library-row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 16.dp),
                ) {
                    items(library, key = { it.manga.id }) { entry ->
                        MangaGridCard(
                            entry = entry,
                            onClick = { onOpenLibraryManga(entry.manga.id) },
                            onLongClick = null,
                            modifier = Modifier.width(CARD_WIDTH),
                        )
                    }
                }
            }
        }

        item(key = "sources-header") {
            SectionHeader(
                title = stringResource(R.string.manga_search_from_sources),
                modifier = Modifier.padding(horizontal = 16.dp),
                actionLabel = stringResource(R.string.manga_choose_sources),
                onAction = onChooseSources,
            )
        }

        if (search.sourceCount == 0) {
            item(key = "no-sources") {
                EmptyState(
                    title = stringResource(R.string.manga_search_no_sources_title),
                    message = stringResource(R.string.manga_search_no_sources_message),
                    icon = FolioIcons.Extension,
                    actionLabel = stringResource(R.string.manga_choose_sources),
                    onAction = onChooseSources,
                )
            }
        }

        items(withResults + searching + failed, key = { it.sourceId }) { result ->
            SourceResultRow(
                result = result,
                onOpen = { manga -> onOpenSourceManga(result.sourceId, manga) },
                onOpenSite = { onOpenSourceWebView(result.sourceId) },
                onRetry = { onRetrySource(result.sourceId) },
            )
        }

        if (foundNothing > 0) {
            item(key = "found-nothing") {
                val text = if (withResults.isEmpty() && searching.isEmpty() && failed.isEmpty()) {
                    stringResource(R.string.manga_search_no_results, search.query)
                } else {
                    pluralStringResource(R.plurals.manga_search_nothing_found, foundNothing, foundNothing)
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceResultRow(
    result: SourceSearchResult,
    onOpen: (SManga) -> Unit,
    onOpenSite: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(result.sourceName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = langLabel(result.lang),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (result.state is SourceSearchState.Loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        when (val state = result.state) {
            is SourceSearchState.Done -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                // Sources sometimes list the same title twice, so items are not keyed by URL.
                items(state.manga) { manga ->
                    SourceMangaCard(manga, result.sourceId, onClick = { onOpen(manga) }, modifier = Modifier.width(CARD_WIDTH))
                }
            }

            is SourceSearchState.Failed -> Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                // The site is always offered: extensions word "visit the website first" in many ways,
                // and it is emphasised when the error is recognisably one of them.
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.needsWebView) {
                        OutlinedButton(onClick = onOpenSite) {
                            Text(stringResource(R.string.manga_open_site))
                        }
                    }
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.action_retry))
                    }
                    if (!state.needsWebView) {
                        TextButton(onClick = onOpenSite) {
                            Text(stringResource(R.string.manga_open_site))
                        }
                    }
                }
            }

            SourceSearchState.Loading -> Unit
        }
    }
}

@Composable
private fun SourceMangaCard(manga: SManga, sourceId: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
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
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
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

/**
 * Which installed sources a search covers. Sources are grouped by language, since one extension
 * often adds the same site in dozens of languages, and a whole language can be switched at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceChooserSheet(
    sources: List<SearchableSource>,
    onSetIncluded: (sourceIds: Collection<Long>, included: Boolean) -> Unit,
    onChooseLanguages: () -> Unit,
    onDismiss: () -> Unit,
) {
    val labels = sources.map { it.lang }.distinct().associateWith { langLabel(it) }
    val groups = remember(sources, labels) {
        sources.groupBy { it.lang }.toList().sortedWith(
            compareBy<Pair<String, List<SearchableSource>>> { langRank(it.first) }.thenBy { labels[it.first] },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "intro") {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.manga_choose_sources), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.manga_choose_sources_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedButton(onClick = onChooseLanguages, modifier = Modifier.padding(top = 12.dp)) {
                        Icon(FolioIcons.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.manga_languages), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }

            if (sources.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.manga_search_no_sources_message),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            groups.forEach { (lang, group) ->
                item(key = "lang-$lang") {
                    val allIncluded = group.all { it.included }
                    ListItem(
                        headlineContent = {
                            Text(labels[lang].orEmpty(), style = MaterialTheme.typography.titleSmall)
                        },
                        supportingContent = { Text("${group.count { it.included }} / ${group.size}") },
                        trailingContent = { Switch(checked = allIncluded, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = allIncluded,
                            role = Role.Switch,
                            onValueChange = { include -> onSetIncluded(group.map { it.id }, include) },
                        ),
                    )
                }
                items(group, key = { it.id }) { source ->
                    ListItem(
                        headlineContent = { Text(source.name) },
                        trailingContent = { Checkbox(checked = source.included, onCheckedChange = null) },
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .toggleable(
                                value = source.included,
                                role = Role.Checkbox,
                                onValueChange = { include -> onSetIncluded(listOf(source.id), include) },
                            ),
                    )
                }
            }
        }
    }
}

/** "all" first, real languages in between, "other" last. */
internal fun langRank(lang: String): Int = when (lang) {
    "all" -> 0
    "other", "" -> 2
    else -> 1
}

@Composable
internal fun langLabel(lang: String): String = when (lang) {
    "all" -> stringResource(R.string.manga_lang_all)
    "other", "" -> stringResource(R.string.manga_lang_other)
    else -> Locale.getDefault().let { device ->
        Locale.forLanguageTag(lang).getDisplayName(device).ifBlank { lang }.replaceFirstChar { it.titlecase(device) }
    }
}
