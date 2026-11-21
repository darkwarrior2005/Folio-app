package app.folio.ui.screens.manga

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.ui.folioViewModel

/**
 * The Manga tab inside the merged Library screen (Phase 1c of the UI redesign). A grid with
 * unread/downloaded counts shown as plain text next to the cover - no badge iconography to keep
 * this tab visually calm next to the book grid.
 */
@Composable
fun MangaLibraryTabContent(
    onOpenManga: (Long) -> Unit,
    onFindMangaOnline: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MangaLibraryViewModel = folioViewModel { MangaLibraryViewModel(it) },
) {
    val library by viewModel.library.collectAsStateWithLifecycle()

    if (library.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No manga yet", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onFindMangaOnline) { Text("Find manga online") }
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(library, key = { it.id }) { manga ->
            MangaCover(
                manga = manga,
                sourceName = viewModel.sourceName(manga.sourceId),
                onClick = { onOpenManga(manga.id) },
            )
        }
    }
}
