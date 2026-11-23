package app.folio.ui.screens.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.SourceLanguages
import app.folio.ui.components.EmptyState
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons
import eu.kanade.tachiyomi.source.ConfigurableSource

/**
 * The sources provided by the installed extensions. Each one leads to its own catalogue.
 */
@Composable
fun BrowseSourcesScreen(
    onBack: () -> Unit,
    onOpenSource: (Long) -> Unit,
    onExtensions: () -> Unit,
    onOpenSettings: (sourceId: Long) -> Unit,
    viewModel: BrowseViewModel = folioViewModel { BrowseViewModel(it) },
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val sourcesEnabled by viewModel.sourcesEnabled.collectAsStateWithLifecycle()
    val hiddenLanguages by viewModel.hiddenLanguages.collectAsStateWithLifecycle()
    val languageChoices by viewModel.languageChoices.collectAsStateWithLifecycle()
    var choosingLanguages by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manga_browse)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FolioIcons.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    LanguagesButton(onClick = { choosingLanguages = true })
                    IconButton(onClick = onExtensions) {
                        Icon(FolioIcons.Extension, stringResource(R.string.manga_extensions))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when {
                !sourcesEnabled -> EmptyState(
                    title = stringResource(R.string.manga_sources_off_title),
                    message = stringResource(R.string.manga_sources_off_message),
                    icon = FolioIcons.Language,
                )

                sources.isEmpty() && hiddenLanguages.isNotEmpty() && languageChoices.isNotEmpty() -> EmptyState(
                    title = stringResource(R.string.manga_no_sources_title),
                    message = stringResource(R.string.manga_sources_hidden_by_language),
                    icon = FolioIcons.Language,
                    actionLabel = stringResource(R.string.manga_languages),
                    onAction = { choosingLanguages = true },
                )

                sources.isEmpty() -> EmptyState(
                    title = stringResource(R.string.manga_no_sources_title),
                    message = stringResource(R.string.manga_no_sources_message),
                    icon = FolioIcons.Extension,
                    actionLabel = stringResource(R.string.manga_extensions),
                    onAction = onExtensions,
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(sources, key = { it.id }) { source ->
                        ListItem(
                            headlineContent = { Text(source.name) },
                            supportingContent = { Text(langLabel(SourceLanguages.normalize(source.lang))) },
                            leadingContent = { Icon(FolioIcons.Language, contentDescription = null) },
                            trailingContent = {
                                if (source is ConfigurableSource) {
                                    IconButton(onClick = { onOpenSettings(source.id) }) {
                                        Icon(FolioIcons.Settings, stringResource(R.string.manga_source_settings))
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onOpenSource(source.id) },
                        )
                    }
                }
            }
        }
    }

    if (choosingLanguages) {
        LanguagePickerSheet(
            choices = languageChoices,
            hidden = hiddenLanguages,
            onSetHidden = viewModel::setHiddenLanguages,
            onDismiss = { choosingLanguages = false },
        )
    }
}
