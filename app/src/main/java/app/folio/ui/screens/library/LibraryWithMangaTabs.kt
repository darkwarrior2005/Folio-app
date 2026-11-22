package app.folio.ui.screens.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.folio.R
import app.folio.ui.screens.manga.MangaLibraryTabContent

/**
 * Library with a Books | Manga switcher. The tabs sit inside each tab's own top bar, under the
 * "Library" title, so the status-bar inset is applied once by that top bar, as on every other
 * screen. (Drawn above the top bar, the tabs ended up under the status bar and the top bar then
 * added the status-bar height a second time.)
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
    onMangaExtensions: () -> Unit,
    onMangaDownloads: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.library_tab_books)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.library_tab_manga)) })
        }
    }

    if (tab == 0) {
        LibraryScreen(
            onOpenBook = onOpenBook,
            onBookDetails = onBookDetails,
            onSearch = onSearch,
            onCollections = onCollections,
            onMusic = onMusic,
            tabs = tabs,
        )
    } else {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.library_title)) },
                        actions = {
                            IconButton(onClick = onMangaDownloads) {
                                Icon(Icons.Rounded.Download, contentDescription = stringResource(R.string.manga_downloads))
                            }
                            IconButton(onClick = onMangaExtensions) {
                                Icon(Icons.Rounded.Extension, contentDescription = stringResource(R.string.manga_extensions))
                            }
                        },
                    )
                    tabs()
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = onFindMangaOnline,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.manga_find_online)) },
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
