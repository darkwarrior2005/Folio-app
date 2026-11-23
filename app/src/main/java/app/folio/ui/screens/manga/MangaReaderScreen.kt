package app.folio.ui.screens.manga

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.manga.source.sourceErrorMessage
import app.folio.ui.components.EmptyState
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons
import kotlinx.coroutines.CancellationException

/**
 * Reads one manga chapter, whether it was downloaded or is being streamed from its source.
 *
 * Deliberately simpler than Folio's book reader: no ink, no highlights, no text selection, since
 * a manga page is an image. Progress is saved per page so the chapter reopens where it was left.
 */
@Composable
fun MangaReaderScreen(
    chapterId: Long,
    onBack: () -> Unit,
    onOpenWebView: (sourceId: Long) -> Unit,
    viewModel: MangaReaderViewModel = folioViewModel(key = "manga-reader-$chapterId") {
        MangaReaderViewModel(it, chapterId)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var chromeVisible by remember { mutableStateOf(true) }

    // Back from passing a site's check: open the chapter again rather than leave the error up.
    var openedSite by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (openedSite) {
            openedSite = false
            if (state.error != null) viewModel.retry()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            if (chromeVisible) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.chapterName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            Text(
                                text = state.mangaTitle,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                FolioIcons.ArrowBack,
                                stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.previousChapterId()?.let(viewModel::open) },
                            enabled = state.hasPrevious,
                        ) {
                            Icon(FolioIcons.SkipPrevious, stringResource(R.string.manga_previous_chapter))
                        }
                        IconButton(
                            onClick = { viewModel.nextChapterId()?.let(viewModel::open) },
                            enabled = state.hasNext,
                        ) {
                            Icon(FolioIcons.SkipNext, stringResource(R.string.manga_next_chapter))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    ),
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        title = stringResource(R.string.manga_chapter_error_title),
                        message = state.error,
                        icon = FolioIcons.CloudOff,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = viewModel::retry,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (viewModel.sourceId != 0L) {
                        OutlinedButton(
                            onClick = {
                                openedSite = true
                                onOpenWebView(viewModel.sourceId)
                            },
                        ) {
                            Text(stringResource(R.string.manga_open_site))
                        }
                    }
                }

                state.pageCount == 0 -> EmptyState(
                    title = stringResource(R.string.manga_chapter_empty_title),
                    message = stringResource(R.string.manga_chapter_empty_message),
                    modifier = Modifier.padding(padding),
                )

                else -> {
                    val pagerState = rememberPagerState(
                        initialPage = state.initialPage,
                        pageCount = { state.pageCount },
                    )

                    LaunchedEffect(pagerState, state.pageCount) {
                        snapshotFlow { pagerState.currentPage }
                            .collect { viewModel.onPageChanged(it) }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { index ->
                        MangaPage(
                            index = index,
                            load = viewModel::page,
                            onTap = { chromeVisible = !chromeVisible },
                        )
                    }

                    if (chromeVisible) {
                        PageCounter(
                            current = pagerState.currentPage + 1,
                            total = state.pageCount,
                            offline = state.offline,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

/** One page, as the reader sees it while it is fetched. */
private sealed interface PageState {
    data object Loading : PageState
    data class Loaded(val bitmap: Bitmap) : PageState
    data class Failed(val message: String) : PageState
}

@Composable
private fun MangaPage(
    index: Int,
    load: suspend (Int, Int) -> Bitmap,
    onTap: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val targetWidthPx = remember(configuration.screenWidthDp) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }
    }

    var attempt by remember { mutableIntStateOf(0) }
    val page by produceState<PageState>(initialValue = PageState.Loading, index, targetWidthPx, attempt) {
        value = PageState.Loading
        value = try {
            PageState.Loaded(load(index, targetWidthPx))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            PageState.Failed(e.sourceErrorMessage())
        } catch (e: OutOfMemoryError) {
            PageState.Failed("This page is too large to show")
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = page) {
            PageState.Loading -> CircularProgressIndicator()

            is PageState.Loaded -> Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.manga_page, index + 1),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )

            is PageState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = stringResource(R.string.manga_page_error, index + 1),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                OutlinedButton(onClick = { attempt++ }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.action_retry), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PageCounter(
    current: Int,
    total: Int,
    offline: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = stringResource(R.string.manga_page_of, current, total),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
            if (offline) {
                Text(
                    text = " · " + stringResource(R.string.manga_offline),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
