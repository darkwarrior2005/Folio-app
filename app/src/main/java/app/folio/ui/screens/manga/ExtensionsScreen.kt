package app.folio.ui.screens.manga

import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.manga.extension.AvailableExtension
import app.folio.manga.extension.InstalledExtension
import app.folio.ui.folioViewModel
import app.folio.ui.screens.lock.DeviceLockGate
import app.folio.ui.screens.lock.rememberDeviceAuth

/** Tabs: Installed & available, and Repositories. Requires the screen lock when it's turned on. */
@Composable
fun ExtensionsScreen(initialTab: Int, onBack: () -> Unit, onSourceSettings: (String) -> Unit) {
    val viewModel: ExtensionsViewModel = folioViewModel { ExtensionsViewModel(it) }
    val lockState = rememberDeviceAuth()
    var tab by remember { mutableIntStateOf(initialTab) }

    Scaffold(topBar = { app.folio.ui.components.BackTopBar("Extensions", onBack) }) { padding ->
        DeviceLockGate(lockState, onCancelled = onBack) {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(padding)) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Extensions") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Repositories") })
                }
                if (tab == 0) {
                    ExtensionsList(viewModel, onSourceSettings)
                } else {
                    RepositoriesList(viewModel)
                }
            }
        }
    }
}

@Composable
private fun ExtensionsList(viewModel: ExtensionsViewModel, onSourceSettings: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            TextField(value = query, onValueChange = { query = it }, placeholder = { Text("Search extensions") })
            if (state.failedRepos.isNotEmpty()) {
                Text("${state.failedRepos.size} of ${state.totalRepos} repositories failed")
            }
        }
        items(state.installed.filter { query.isBlank() || it.name.contains(query, true) }, key = { it.packageName }) { ext ->
            InstalledExtensionRow(ext, viewModel, onSourceSettings)
        }
        items(
            state.available.filter { it.packageName !in state.installed.map(InstalledExtension::packageName) }
                .filter { query.isBlank() || it.name.contains(query, true) },
            key = { it.packageName + it.repoBaseUrl },
        ) { ext ->
            AvailableExtensionRow(ext, viewModel)
        }
    }
}

@Composable
private fun InstalledExtensionRow(ext: InstalledExtension, viewModel: ExtensionsViewModel, onSourceSettings: (String) -> Unit) {
    val disabled by viewModel.disabledExtensions.collectAsStateWithLifecycle()
    ListItem(
        headlineContent = { Text(ext.name) },
        supportingContent = { Text(ext.versionName) },
        trailingContent = {
            Row {
                Switch(
                    checked = ext.packageName !in disabled,
                    onCheckedChange = { viewModel.setEnabled(ext.packageName, it) },
                )
                IconButton(onClick = { onSourceSettings(ext.packageName) }) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Settings, contentDescription = "Settings")
                }
                IconButton(onClick = { viewModel.uninstall(ext.packageName) }) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Uninstall")
                }
            }
        },
    )
}

@Composable
private fun AvailableExtensionRow(ext: AvailableExtension, viewModel: ExtensionsViewModel) {
    var step by remember { mutableStateOf<String?>(null) }
    ListItem(
        headlineContent = { Text(ext.name) },
        supportingContent = { Text(step ?: ext.versionName) },
        trailingContent = {
            Button(onClick = { viewModel.install(ext) { s -> step = s::class.simpleName } }) { Text("Install") }
        },
    )
}

@Composable
private fun RepositoriesList(viewModel: ExtensionsViewModel) {
    val repos by viewModel.repos.collectAsStateWithLifecycle(initialValue = emptyList())
    var newUrl by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth()) {
                TextField(value = newUrl, onValueChange = { newUrl = it }, placeholder = { Text("Repository index.min.json / index.pb / repo.json URL") })
                Button(onClick = { viewModel.addRepo(newUrl); newUrl = "" }) { Text("Add") }
            }
        }
        items(repos, key = { it.id }) { repo ->
            ListItem(
                headlineContent = { Text(repo.name.ifBlank { repo.baseUrl }) },
                supportingContent = { Text(repo.baseUrl) },
                trailingContent = {
                    IconButton(onClick = { viewModel.removeRepo(repo.id) }) {
                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "Remove")
                    }
                },
            )
        }
    }
}
