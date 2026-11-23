package app.folio.ui.screens.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.SourceLanguages
import app.folio.data.db.ExtensionRepoEntity
import app.folio.manga.extension.model.Extension
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.EmptyState
import app.folio.ui.components.SectionHeader
import app.folio.ui.components.SourcesOffEmptyState
import app.folio.ui.components.TextInputDialog
import app.folio.ui.folioViewModel
import app.folio.ui.icons.FolioIcons
import app.folio.ui.screens.lock.DeviceLockGate
import eu.kanade.tachiyomi.source.ConfigurableSource

/**
 * Installed extensions, the ones the user's repositories offer, and the repositories
 * themselves — one screen with two tabs, so adding a repo and seeing what it offers are no
 * longer a navigation hop apart.
 *
 * An extension that is installed but not running says why, and an untrusted one can be accepted
 * here — which is the moment the user agrees to run somebody else's code inside Folio.
 */
@Composable
fun ExtensionsScreen(
    onBack: () -> Unit,
    onOpenMangaSettings: () -> Unit,
    onOpenSourceSettings: (sourceId: Long) -> Unit,
    initialTab: Int = 0,
    viewModel: ExtensionsViewModel = folioViewModel { ExtensionsViewModel(it) },
) {
    // Extensions run other people's code inside Folio, so managing them asks for the phone's
    // owner first: fingerprint, face or screen lock (turned off in Manga settings).
    val lockRequired by viewModel.lockRequired.collectAsStateWithLifecycle()
    DeviceLockGate(
        required = lockRequired,
        title = stringResource(R.string.manga_extensions),
        message = stringResource(R.string.manga_extensions_locked),
        onBack = onBack,
    ) {
        ExtensionsContent(onBack, onOpenMangaSettings, onOpenSourceSettings, initialTab, viewModel)
    }
}

@Composable
private fun ExtensionsContent(
    onBack: () -> Unit,
    onOpenMangaSettings: () -> Unit,
    onOpenSourceSettings: (sourceId: Long) -> Unit,
    initialTab: Int,
    viewModel: ExtensionsViewModel,
) {
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val available by viewModel.available.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val sourcesEnabled by viewModel.sourcesEnabled.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val repoRefreshIssue by viewModel.repoRefreshIssue.collectAsStateWithLifecycle()
    val hiddenLanguages by viewModel.hiddenLanguages.collectAsStateWithLifecycle()
    val disabledExtensions by viewModel.disabledExtensions.collectAsStateWithLifecycle()
    var choosingLanguages by remember { mutableStateOf(false) }

    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val snackbar = remember { SnackbarHostState() }
    var pendingTrust by remember { mutableStateOf<Extension.NotLoaded?>(null) }
    var pendingUninstall by remember { mutableStateOf<Extension.Installed?>(null) }
    var showAddRepo by remember { mutableStateOf(false) }
    var pendingRepoRemoval by remember { mutableStateOf<ExtensionRepoEntity?>(null) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    repoRefreshIssue?.let { (failed, total) ->
        val text = pluralStringResource(R.plurals.manga_repo_refresh_failed, failed, failed, total)
        LaunchedEffect(failed, total) {
            snackbar.showSnackbar(text)
            viewModel.clearRepoRefreshIssue()
        }
    }

    // Not saveable: instance state can be written to disk, and a search should leave no trace.
    var extensionQuery by remember { mutableStateOf("") }
    val installedPackages = installed.map { it.pkgName }.toSet()
    val allUpdates = remember(installed, available) { viewModel.updates() }
    val updates = allUpdates.filter { it.matches(extensionQuery) }
    val shownInstalled = installed.filter { it.matches(extensionQuery) }
    val installable = available.filter {
        it.pkgName !in installedPackages &&
            it.matches(extensionQuery) &&
            SourceLanguages.isExtensionShown(it.lang, it.sources.map { source -> source.lang }, hiddenLanguages)
    }
    val hiddenByLanguage = hiddenLanguages.isNotEmpty() &&
        available.count { it.pkgName !in installedPackages && it.matches(extensionQuery) } > installable.size
    val languageChoices = remember(available, installed, hiddenLanguages) {
        SourceLanguages.choices(
            available.flatMap { extension -> extension.sources.map { it.lang } + extension.lang } +
                installed.filterIsInstance<Extension.Loaded>().flatMap { extension -> extension.sources.map { it.lang } },
            hiddenLanguages,
        )
    }
    val nothingMatches = extensionQuery.isNotBlank() &&
        updates.isEmpty() && shownInstalled.isEmpty() && installable.isEmpty()
    // Repositories often mirror each other's packages; with more than one added, say which is which.
    val repoNames = remember(repos) { if (repos.size > 1) repos.associate { it.baseUrl to it.name } else emptyMap() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manga_extensions)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(FolioIcons.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (tab == 0) {
                        LanguagesButton(onClick = { choosingLanguages = true })
                        IconButton(onClick = viewModel::refresh, enabled = !refreshing) {
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.padding(4.dp))
                            } else {
                                Icon(FolioIcons.Refresh, stringResource(R.string.action_refresh))
                            }
                        }
                    } else {
                        IconButton(onClick = { showAddRepo = true }) {
                            Icon(FolioIcons.Add, stringResource(R.string.manga_add_repo))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.manga_extensions_tab, R.string.manga_repos).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) })
                }
            }

            if (tab == 0) {
                if (!sourcesEnabled) {
                    SourcesOffEmptyState(onOpenMangaSettings = onOpenMangaSettings)
                } else {
                    if (repos.isNotEmpty() || installed.isNotEmpty()) {
                        OutlinedTextField(
                            value = extensionQuery,
                            onValueChange = { extensionQuery = it },
                            label = { Text(stringResource(R.string.manga_extensions_search)) },
                            leadingIcon = { Icon(FolioIcons.Search, contentDescription = null) },
                            trailingIcon = {
                                if (extensionQuery.isNotEmpty()) {
                                    IconButton(onClick = { extensionQuery = "" }) {
                                        Icon(FolioIcons.Close, stringResource(R.string.action_clear_search))
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        )
                    }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    ) {
                        if (nothingMatches) {
                            item {
                                Text(
                                    text = stringResource(R.string.manga_extensions_no_match, extensionQuery.trim()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (hiddenByLanguage) {
                            item {
                                TextButton(onClick = { choosingLanguages = true }) {
                                    Text(stringResource(R.string.manga_extensions_hidden_by_language))
                                }
                            }
                        }

                        if (repos.isEmpty()) {
                            item {
                                EmptyState(
                                    title = stringResource(R.string.manga_no_repos_title),
                                    message = stringResource(R.string.manga_no_repos_message),
                                    icon = FolioIcons.Add,
                                    actionLabel = stringResource(R.string.manga_add_repo),
                                    onAction = { tab = 1 },
                                )
                            }
                        }

                        if (updates.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.manga_extension_updates)) }
                            items(updates, key = { "update-${it.repoUrl}|${it.pkgName}" }) { extension ->
                                ListItem(
                                    headlineContent = { Text(extension.name) },
                                    supportingContent = {
                                        Text(listOfNotNull(extension.versionName, repoNames[extension.repoUrl]).joinToString(" · "))
                                    },
                                    trailingContent = {
                                        Button(onClick = { viewModel.install(extension) }) {
                                            Text(stringResource(R.string.manga_update))
                                        }
                                    },
                                )
                            }
                        }

                        if (shownInstalled.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.manga_extensions_installed)) }
                            items(shownInstalled, key = { "installed-${it.pkgName}" }) { extension ->
                                InstalledRow(
                                    extension = extension,
                                    enabled = extension.pkgName !in disabledExtensions,
                                    onSetEnabled = { viewModel.setEnabled(extension.pkgName, it) },
                                    onOpenSourceSettings = onOpenSourceSettings,
                                    onAccept = { pendingTrust = it },
                                    onUninstall = { pendingUninstall = extension },
                                )
                            }
                        }

                        if (installable.isNotEmpty()) {
                            item { SectionHeader(stringResource(R.string.manga_extensions_available)) }
                            items(installable, key = { "available-${it.repoUrl}|${it.pkgName}" }) { extension ->
                                ListItem(
                                    headlineContent = { Text(extension.name) },
                                    supportingContent = {
                                        Text(
                                            listOfNotNull(
                                                extension.lang.uppercase().ifBlank { null },
                                                extension.versionName,
                                                repoNames[extension.repoUrl],
                                            ).joinToString(" · "),
                                        )
                                    },
                                    trailingContent = {
                                        OutlinedButton(onClick = { viewModel.install(extension) }) {
                                            Text(stringResource(R.string.manga_install))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                if (repos.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.manga_no_repos_title),
                        message = stringResource(R.string.manga_no_repos_message),
                        icon = FolioIcons.Inventory2,
                        actionLabel = stringResource(R.string.manga_add_repo),
                        onAction = { showAddRepo = true },
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(repos, key = { it.baseUrl }) { repo ->
                            ListItem(
                                headlineContent = { Text(repo.name) },
                                supportingContent = { Text(repo.baseUrl) },
                                trailingContent = {
                                    IconButton(onClick = { pendingRepoRemoval = repo }) {
                                        Icon(FolioIcons.Delete, stringResource(R.string.action_remove))
                                    }
                                },
                            )
                        }
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

    pendingTrust?.let { extension ->
        val reason = extension.reason
        if (reason is Extension.NotLoaded.Reason.Untrusted) {
            AlertDialog(
                onDismissRequest = { pendingTrust = null },
                title = { Text(stringResource(R.string.manga_trust_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.manga_trust_message, extension.name))
                        Text(
                            text = reason.signature,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.accept(extension)
                        pendingTrust = null
                    }) { Text(stringResource(R.string.manga_trust_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingTrust = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }

    pendingUninstall?.let { extension ->
        ConfirmDialog(
            title = stringResource(R.string.manga_uninstall_title),
            message = stringResource(R.string.manga_uninstall_message, extension.name),
            confirmLabel = stringResource(R.string.manga_uninstall),
            destructive = true,
            onConfirm = {
                viewModel.uninstall(extension.pkgName)
                pendingUninstall = null
            },
            onDismiss = { pendingUninstall = null },
        )
    }

    if (showAddRepo) {
        TextInputDialog(
            title = stringResource(R.string.manga_add_repo),
            label = stringResource(R.string.manga_repo_url_label),
            message = stringResource(R.string.manga_repo_url_hint),
            confirmLabel = stringResource(R.string.action_add),
            onConfirm = { url ->
                viewModel.addRepo(url)
                showAddRepo = false
            },
            onDismiss = { showAddRepo = false },
        )
    }

    pendingRepoRemoval?.let { repo ->
        ConfirmDialog(
            title = stringResource(R.string.manga_remove_repo_title),
            message = stringResource(R.string.manga_remove_repo_message, repo.name),
            confirmLabel = stringResource(R.string.action_remove),
            destructive = true,
            onConfirm = {
                viewModel.removeRepo(repo.baseUrl)
                pendingRepoRemoval = null
            },
            onDismiss = { pendingRepoRemoval = null },
        )
    }
}

@Composable
private fun InstalledRow(
    extension: Extension.Installed,
    enabled: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onOpenSourceSettings: (sourceId: Long) -> Unit,
    onAccept: (Extension.NotLoaded) -> Unit,
    onUninstall: () -> Unit,
) {
    val configurable = (extension as? Extension.Loaded)?.sources?.filterIsInstance<ConfigurableSource>().orEmpty()
    var pickingSource by remember { mutableStateOf(false) }
    val subtitle = when (extension) {
        is Extension.Loaded -> if (enabled) {
            "${extension.lang.uppercase()} · ${extension.versionName} · " + pluralSources(extension.sources.size)
        } else {
            stringResource(R.string.manga_extension_turned_off)
        }

        is Extension.NotLoaded -> stringResource(extension.reason.labelRes())
    }

    ListItem(
        headlineContent = { Text(extension.name) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (extension is Extension.NotLoaded &&
                    extension.reason is Extension.NotLoaded.Reason.Untrusted
                ) {
                    Button(onClick = { onAccept(extension) }) {
                        Text(stringResource(R.string.manga_trust_confirm))
                    }
                }
                if (configurable.isNotEmpty() && enabled) {
                    Box {
                        IconButton(onClick = {
                            if (configurable.size == 1) onOpenSourceSettings(configurable.single().id) else pickingSource = true
                        }) {
                            Icon(FolioIcons.Settings, stringResource(R.string.manga_source_settings))
                        }
                        // One extension often adds the same site in many languages, each with its own settings.
                        DropdownMenu(expanded = pickingSource, onDismissRequest = { pickingSource = false }) {
                            configurable.forEach { source ->
                                DropdownMenuItem(
                                    text = { Text(source.toString()) },
                                    onClick = {
                                        pickingSource = false
                                        onOpenSourceSettings(source.id)
                                    },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onUninstall) {
                    Icon(FolioIcons.Delete, stringResource(R.string.manga_uninstall))
                }
                if (extension is Extension.Loaded) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = onSetEnabled,
                        modifier = Modifier.semantics {
                            contentDescription = extension.name
                        },
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun pluralSources(count: Int): String =
    pluralStringResource(R.plurals.manga_source_count, count, count)

private fun Extension.NotLoaded.Reason.labelRes(): Int = when (this) {
    Extension.NotLoaded.Reason.Malformed -> R.string.manga_not_loaded_malformed
    Extension.NotLoaded.Reason.UnsupportedLibVersion -> R.string.manga_not_loaded_unsupported
    Extension.NotLoaded.Reason.Unsigned -> R.string.manga_not_loaded_unsigned
    is Extension.NotLoaded.Reason.Untrusted -> R.string.manga_not_loaded_untrusted
    Extension.NotLoaded.Reason.Filtered -> R.string.manga_not_loaded_filtered
    is Extension.NotLoaded.Reason.Failed -> R.string.manga_not_loaded_failed
}

/** Matches the extension's name, package, language code, or the name of a source it adds. */
private fun Extension.matches(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    if (name.contains(needle, ignoreCase = true) || pkgName.contains(needle, ignoreCase = true)) return true
    if (lang.equals(needle, ignoreCase = true)) return true
    return when (this) {
        is Extension.Available -> sources.any { it.name.contains(needle, ignoreCase = true) }
        is Extension.Loaded -> sources.any { it.name.contains(needle, ignoreCase = true) }
        is Extension.NotLoaded -> false
    }
}
