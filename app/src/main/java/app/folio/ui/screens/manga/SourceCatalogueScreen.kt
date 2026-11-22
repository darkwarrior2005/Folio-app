package app.folio.ui.screens.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.ui.folioViewModel
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.launch

/** Popular, latest and search listings for one source, with simple "load more" paging. */
@Composable
fun SourceCatalogueScreen(
    sourceId: Long,
    onOpenManga: (Long) -> Unit,
    onNeedsWebView: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SourceCatalogueViewModel = folioViewModel(key = "catalogue-$sourceId") {
        SourceCatalogueViewModel(it, sourceId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            app.folio.ui.components.BackTopBar(
                onBack = onBack,
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search this source") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = {
                                if (query.isBlank()) viewModel.switchMode(CatalogueMode.POPULAR) else viewModel.search(query.trim())
                            },
                        ),
                    )
                },
            )
        },
    ) { padding ->
        if (state.error != null && state.manga.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(state.error.orEmpty())
            }
            return@Scaffold
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            items(state.manga, key = { it.url }) { manga ->
                CatalogueMangaCell(manga) {
                    scope.launch {
                        val id = viewModel.addToLibrary(manga)
                        onOpenManga(id)
                    }
                }
            }
            if (state.hasNextPage) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (state.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        Button(onClick = { viewModel.loadMore() }) { Text("Load more") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogueMangaCell(manga: SManga, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }) {
        AsyncImage(
            model = manga.thumbnail_url,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
        )
        Text(manga.title, maxLines = 2)
    }
}
