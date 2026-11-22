package app.folio.ui.screens.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.core.model.SourceLanguages
import app.folio.manga.extension.AvailableExtension
import app.folio.manga.extension.InstallStep
import app.folio.manga.extension.InstalledExtension
import app.folio.ui.components.BackTopBar
import app.folio.ui.folioViewModel
import app.folio.ui.screens.lock.DeviceLockGate
import app.folio.ui.screens.lock.rememberDeviceAuth
import coil3.compose.AsyncImage

/** Tabs: Extensions (installed and available) and Repositories. Behind the screen lock. */
@Composable
fun ExtensionsScreen(initialTab: Int, onBack: () -> Unit, onSourceSettings: (String) -> Unit) {
    val viewModel: ExtensionsViewModel = folioViewModel { ExtensionsViewModel(it) }
    val sourcesEnabled by viewModel.sourcesEnabled.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lockState = rememberDeviceAuth()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }

    Scaffold(
        topBar = {
            BackTopBar("Extensions", onBack) {
                if (sourcesEnabled == true) {
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        },
    ) { padding ->
        DeviceLockGate(lockState, onCancelled = onBack) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                when (sourcesEnabled) {
                    null -> Unit
                    false -> SourcesOff(onTurnOn = viewModel::turnOnSources)
                    true -> {
                        PrimaryTabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Extensions") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Repositories") })
                        }
                        if (state.loading) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            Spacer(Modifier.height(4.dp))
                        }
                        if (tab == 0) {
                            ExtensionsTab(viewModel, onSourceSettings, onAddRepository = { tab = 1 })
                        } else {
                            RepositoriesTab(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesOff(onTurnOn: () -> Unit) {
    CenteredMessage(
        title = "Manga sources are off",
        body = "Extensions download and run code from the internet, so they only work with manga " +
            "sources turned on. Nothing connects until you do.",
        action = { Button(onClick = onTurnOn) { Text("Turn on manga sources") } },
    )
}

@Composable
private fun ExtensionsTab(
    viewModel: ExtensionsViewModel,
    onSourceSettings: (String) -> Unit,
    onAddRepository: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    val disabled by viewModel.disabledExtensions.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    fun matches(name: String) = query.isBlank() || name.contains(query.trim(), ignoreCase = true)
    val installedPackages = state.installed.map(InstalledExtension::packageName).toSet()
    val installed = state.installed.filter { matches(it.name) }
    val available = state.available
        .filter { it.packageName !in installedPackages && matches(it.name) }
        .distinctBy { it.packageName }
        .sortedBy { it.name.lowercase() }

    if (repos.isEmpty() && state.installed.isEmpty()) {
        CenteredMessage(
            title = "No repositories yet",
            body = "Extensions come from repositories. Add one to see the extensions it offers.",
            action = { Button(onClick = onAddRepository) { Text("Add a repository") } },
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search extensions") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
        }
        if (state.failedRepos.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${state.failedRepos.size} of ${state.totalRepos} repositories couldn't be loaded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::refresh) { Text("Try again") }
                    }
                }
            }
        }
        if (installed.isNotEmpty()) {
            sectionHeader("Installed (${installed.size})")
            items(installed, key = { "installed:" + it.packageName }) { ext ->
                InstalledExtensionRow(
                    ext = ext,
                    enabled = ext.packageName !in disabled,
                    onEnabledChange = { viewModel.setEnabled(ext.packageName, it) },
                    onSettings = { onSourceSettings(ext.packageName) },
                    onUninstall = { viewModel.uninstall(ext.packageName) },
                )
            }
        }
        if (available.isNotEmpty()) {
            sectionHeader("Available (${available.size})")
            items(available, key = { "available:" + it.packageName }) { ext ->
                AvailableExtensionRow(ext, state.installSteps[ext.packageName], onInstall = { viewModel.install(ext) })
            }
        }
        if (installed.isEmpty() && available.isEmpty() && !state.loading) {
            item {
                Text(
                    if (query.isBlank()) "No extensions found in your repositories." else "No extensions match \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
    }
}

private fun LazyListScope.sectionHeader(title: String) {
    item(key = "header:$title") {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun ExtensionIcon(url: String?) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InstalledExtensionRow(
    ext: InstalledExtension,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onUninstall: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = { ExtensionIcon(null) },
        headlineContent = { Text(ext.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                buildString {
                    append("v").append(ext.versionName)
                    if (ext.isNsfw) append(" · 18+")
                    if (!enabled) append(" · Off")
                },
                maxLines = 1,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                            onClick = { menu = false; onSettings() },
                        )
                        DropdownMenuItem(
                            text = { Text("Uninstall") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menu = false; onUninstall() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun AvailableExtensionRow(ext: AvailableExtension, step: InstallStep?, onInstall: () -> Unit) {
    ListItem(
        leadingContent = { ExtensionIcon(ext.iconUrl) },
        headlineContent = { Text(ext.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            when (step) {
                is InstallStep.Error -> Text(
                    step.message,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                else -> Text(
                    buildString {
                        append(SourceLanguages.displayName(ext.lang.ifBlank { SourceLanguages.SHOW_ALL }))
                        append(" · v").append(ext.versionName)
                        if (ext.isNsfw) append(" · 18+")
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            when (step) {
                InstallStep.Downloading, InstallStep.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        if (step == InstallStep.Downloading) "  Downloading" else "  Installing",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                InstallStep.Success -> Text("Installed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                is InstallStep.Error, null -> FilledTonalButton(onClick = onInstall) {
                    Text(if (step is InstallStep.Error) "Retry" else "Install")
                }
            }
        },
    )
}

@Composable
private fun RepositoriesTab(viewModel: ExtensionsViewModel) {
    val repos by viewModel.repos.collectAsStateWithLifecycle()
    var newUrl by rememberSaveable { mutableStateOf("") }

    fun add() {
        if (newUrl.isNotBlank()) {
            viewModel.addRepo(newUrl.trim())
            newUrl = ""
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "Paste a repository's address. Its repo.json, index.min.json or index.pb link all work.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = newUrl,
                    onValueChange = { newUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Repository address") },
                    placeholder = { Text("https://…/index.min.json") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { add() }, enabled = newUrl.isNotBlank()) { Text("Add repository") }
                }
            }
        }
        sectionHeader("Your repositories")
        if (repos.isEmpty()) {
            item {
                Text(
                    "None yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(repos, key = { it.id }) { repo ->
            ListItem(
                headlineContent = {
                    Text(repo.name.ifBlank { repo.baseUrl.substringAfter("://").substringBefore('/') }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = { Text(repo.baseUrl, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    IconButton(onClick = { viewModel.removeRepo(repo.id) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Remove repository")
                    }
                },
            )
        }
    }
}

@Composable
private fun CenteredMessage(title: String, body: String, action: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Extension,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
