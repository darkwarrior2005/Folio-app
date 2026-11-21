package app.folio.ui.screens.manga

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.reader.comic.ComicSource
import app.folio.reader.comic.ComicSources
import app.folio.ui.folioViewModel

/** A pager over a manga chapter's pages. Each page shows its own error, with Try again. */
@Composable
fun MangaReaderScreen(mangaId: Long, chapterId: Long, onBack: () -> Unit) {
    val viewModel: MangaReaderViewModel = folioViewModel(key = "reader-$mangaId-$chapterId") {
        MangaReaderViewModel(it, mangaId, chapterId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is ReaderLoadState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is ReaderLoadState.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.reason)
                Button(onClick = onBack) { Text("Back") }
            }
        }
        is ReaderLoadState.Loaded -> ComicPager(current.comic, onPageChanged = { page -> viewModel.setProgress(page, current.comic.pageCount) })
    }

    LaunchedEffect(state) {
        if (state is ReaderLoadState.Loaded) viewModel.markRead()
    }
}

@Composable
private fun ComicPager(comic: ComicSource, onPageChanged: (Int) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { comic.pageCount })

    LaunchedEffect(pagerState.currentPage) { onPageChanged(pagerState.currentPage) }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().background(Color.Black)) { index ->
        ComicPage(comic, index)
    }
}

@Composable
private fun ComicPage(comic: ComicSource, index: Int) {
    var retryToken by remember { mutableStateOf(0) }
    val bytesState = produceState<Result<ByteArray>?>(initialValue = null, index, retryToken) {
        value = try {
            comic.pageBytes(index)?.let { Result.success(it) } ?: Result.failure(Exception("No data"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        val result = bytesState.value
        when {
            result == null -> CircularProgressIndicator()
            result.isFailure -> androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Couldn't load this page")
                Text(result.exceptionOrNull()?.message ?: "", color = Color.Gray)
                Button(onClick = { retryToken++ }) { Text("Try again") }
            }
            else -> {
                val bytes = result.getOrThrow()
                val bitmap = remember(bytes) { ComicSources.decode(bytes, 1080) }
                if (bitmap == null) {
                    Text("This page couldn't be decoded (${bytes.size} bytes)")
                } else {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
