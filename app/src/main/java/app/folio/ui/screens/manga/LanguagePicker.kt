package app.folio.ui.screens.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.folio.AppContainer
import app.folio.R
import app.folio.core.model.SourceLanguages
import app.folio.ui.icons.FolioIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale

/** The source languages the user hid; see [SourceLanguages]. */
internal fun AppContainer.observeHiddenLanguages(): Flow<Set<String>> =
    settings.settings.map { it.manga.hiddenLanguages }.distinctUntilChanged()

internal suspend fun AppContainer.setHiddenLanguages(hidden: Set<String>) {
    settings.update { it.copy(manga = it.manga.copy(hiddenLanguages = hidden)) }
}

/** The top-bar button that opens [LanguagePickerSheet]. */
@Composable
internal fun LanguagesButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(FolioIcons.Translate, stringResource(R.string.manga_languages))
    }
}

/**
 * Which source languages to show. One choice for the whole app: it filters the extensions on
 * offer, the sources to browse, and the sources a search across sources asks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LanguagePickerSheet(
    choices: List<String>,
    hidden: Set<String>,
    onSetHidden: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val labels = choices.associateWith { langLabel(it) }
    val sorted = choices.sortedWith(compareBy<String> { langRank(it) }.thenBy { labels[it] })
    val device = Locale.getDefault()
    val deviceLabel = device.getDisplayLanguage(device).replaceFirstChar { it.titlecase(device) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "intro") {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.manga_languages), style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.manga_languages_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onSetHidden(emptySet()) }) {
                            Text(stringResource(R.string.manga_languages_all))
                        }
                        if (deviceLabel.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onSetHidden(SourceLanguages.hideAllBut(choices, listOf(device.language))) },
                            ) {
                                Text(stringResource(R.string.manga_languages_only, deviceLabel))
                            }
                        }
                    }
                }
            }

            if (choices.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.manga_languages_none),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            items(sorted, key = { it }) { lang ->
                val shown = lang !in hidden
                ListItem(
                    headlineContent = { Text(labels[lang].orEmpty()) },
                    supportingContent = { if (lang != SourceLanguages.ALL && lang != SourceLanguages.OTHER) Text(lang) },
                    trailingContent = { Checkbox(checked = shown, onCheckedChange = null) },
                    modifier = Modifier.toggleable(
                        value = shown,
                        role = Role.Checkbox,
                        onValueChange = { show -> onSetHidden(if (show) hidden - lang else hidden + lang) },
                    ),
                )
            }
        }
    }
}
