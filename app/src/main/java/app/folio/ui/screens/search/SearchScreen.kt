package app.folio.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.SearchQuery
import app.folio.ui.components.BookCover
import app.folio.ui.components.EmptyState
import app.folio.ui.components.SectionHeader
import app.folio.ui.folioViewModel
import app.folio.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenLocation: (Long, String) -> Unit,
    viewModel: SearchViewModel = folioViewModel { SearchViewModel(it) },
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val books by viewModel.bookResults.collectAsStateWithLifecycle()
    val texts by viewModel.textResults.collectAsStateWithLifecycle()
    val annotations by viewModel.annotationResults.collectAsStateWithLifecycle()
    val titles by viewModel.titles.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.action_search)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenPadding, vertical = 8.dp),
            )

            if (searching) {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.width(24.dp).height(24.dp))
                }
            }

            val nothingFound = books.isEmpty() && texts.isEmpty() && annotations.isEmpty()
            if (query.length >= 2 && nothingFound && !searching) {
                EmptyState(
                    title = stringResource(R.string.search_no_results, query),
                    modifier = Modifier.fillMaxSize(),
                )
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = spacing.screenPadding,
                    end = spacing.screenPadding,
                    bottom = 64.dp,
                ),
            ) {
                if (books.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.search_books)) }
                    items(books, key = { "book-${it.id}" }) { book ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenBook(book.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BookCover(
                                title = book.title,
                                coverPath = book.coverPath,
                                format = book.format,
                                modifier = Modifier
                                    .width(38.dp)
                                    .aspectRatio(0.66f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                book.author?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (texts.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.search_inside_books)) }
                    items(texts, key = { "text-${it.bookId}-${it.snippet.hashCode()}" }) { hit ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    hit.location?.let { location ->
                                        onOpenLocation(hit.bookId, app.folio.data.db.LocationCodec.encode(location))
                                    } ?: onOpenBook(hit.bookId)
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = titles[hit.bookId].orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = highlightSnippet(hit.snippet),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (hit.label.isNotBlank()) {
                                Text(
                                    text = hit.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (annotations.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.search_notes)) }
                    items(annotations, key = { "anno-${it.kind}-${it.bookId}-${it.text.hashCode()}" }) { hit ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenLocation(hit.bookId, hit.location) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = hit.bookTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = hit.text,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun highlightSnippet(snippet: String) = buildAnnotatedString {
    SearchQuery.parseSnippet(snippet).forEach { (text, matched) ->
        if (matched) {
            withStyle(
                SpanStyle(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                ),
            ) { append(text) }
        } else {
            append(text)
        }
    }
}
