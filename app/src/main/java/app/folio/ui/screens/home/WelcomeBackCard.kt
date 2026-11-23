package app.folio.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.folio.R
import app.folio.core.model.LibraryBook
import app.folio.ui.components.BookCover
import app.folio.ui.icons.FolioIcons
import kotlinx.coroutines.launch

/**
 * The first thing on the home screen: the book you were reading, one tap from where you left it,
 * and a "Surprise me" for when you would rather not choose.
 */
@Composable
internal fun WelcomeBackCard(
    book: LibraryBook?,
    onOpenBook: (Long) -> Unit,
    surprise: suspend () -> Long?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (book != null) {
                BookCover(
                    title = book.title,
                    coverPath = book.coverPath,
                    format = book.format,
                    modifier = Modifier.width(52.dp).height(76.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = if (book != null) 14.dp else 2.dp),
            ) {
                Text(
                    text = stringResource(if (book != null) R.string.welcome_back else R.string.welcome_pick),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                if (book != null) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { book.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (book != null) {
                        Button(onClick = { onOpenBook(book.id) }) { Text(stringResource(R.string.welcome_continue)) }
                    }
                    OutlinedButton(onClick = { scope.launch { surprise()?.let(onOpenBook) } }) {
                        Icon(FolioIcons.Dice, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.surprise_me))
                    }
                }
            }
        }
    }
}
