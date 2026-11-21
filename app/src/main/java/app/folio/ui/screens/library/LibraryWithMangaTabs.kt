package app.folio.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.folio.ui.screens.manga.MangaLibraryTabContent

/**
 * Phase 1c of the UI redesign: Library gets a Books | Manga TabRow instead of Manga having its
 * own separate route and bottom-nav icon. Each tab keeps its own screen/FAB rather than a single
 * shared one, to avoid reworking LibraryScreen's existing FAB and selection logic wholesale.
 */
@Composable
fun LibraryWithMangaTabs(
    onOpenBook: (Long) -> Unit,
    onBookDetails: (Long) -> Unit,
    onSearch: () -> Unit,
    onCollections: () -> Unit,
    onMusic: () -> Unit,
    onOpenManga: (Long) -> Unit,
    onFindMangaOnline: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Books") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Manga") })
        }
        when (tab) {
            0 -> LibraryScreen(
                onOpenBook = onOpenBook,
                onBookDetails = onBookDetails,
                onSearch = onSearch,
                onCollections = onCollections,
                onMusic = onMusic,
            )
            else -> Scaffold(
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = onFindMangaOnline,
                        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                        text = { Text("Find manga online") },
                    )
                },
            ) { padding ->
                MangaLibraryTabContent(
                    onOpenManga = onOpenManga,
                    onFindMangaOnline = onFindMangaOnline,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
    }
}
