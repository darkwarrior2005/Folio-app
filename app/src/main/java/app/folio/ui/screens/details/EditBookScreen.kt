package app.folio.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.ReadingStatus
import app.folio.ui.components.SectionHeader
import app.folio.ui.components.TextInputDialog
import app.folio.ui.folioViewModel
import app.folio.ui.screens.library.label
import app.folio.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditBookScreen(
    bookId: Long,
    onBack: () -> Unit,
    viewModel: EditBookViewModel = folioViewModel(key = "edit-$bookId") { EditBookViewModel(it, bookId) },
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val imported by viewModel.imported.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    var tagInput by remember { mutableStateOf("") }
    var showNewCategory by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_book)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onBack) }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        val current = form ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.screenPadding)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EditField(
                label = stringResource(R.string.field_title),
                value = current.title,
                importedValue = imported.title,
                onValueChange = { value -> viewModel.edit { it.copy(title = value) } },
                onReset = { value -> viewModel.edit { it.copy(title = value) } },
            )

            EditField(
                label = stringResource(R.string.field_author),
                value = current.author,
                importedValue = imported.author,
                onValueChange = { value -> viewModel.edit { it.copy(author = value) } },
                onReset = { value -> viewModel.edit { it.copy(author = value) } },
            )

            EditField(
                label = stringResource(R.string.field_series),
                value = current.series,
                importedValue = imported.series,
                onValueChange = { value -> viewModel.edit { it.copy(series = value) } },
                onReset = { value -> viewModel.edit { it.copy(series = value) } },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = current.volume,
                    onValueChange = { value -> viewModel.edit { it.copy(volume = value) } },
                    label = { Text(stringResource(R.string.field_volume)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = current.year,
                    onValueChange = { value -> viewModel.edit { it.copy(year = value) } },
                    label = { Text(stringResource(R.string.field_year)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = current.language,
                    onValueChange = { value -> viewModel.edit { it.copy(language = value) } },
                    label = { Text(stringResource(R.string.field_language)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = current.publisher,
                    onValueChange = { value -> viewModel.edit { it.copy(publisher = value) } },
                    label = { Text(stringResource(R.string.field_publisher)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = current.description,
                onValueChange = { value -> viewModel.edit { it.copy(description = value) } },
                label = { Text(stringResource(R.string.field_description)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                SectionHeader(stringResource(R.string.field_category))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = current.categoryId == null,
                        onClick = { viewModel.edit { it.copy(categoryId = null) } },
                        label = { Text(stringResource(R.string.no_category)) },
                    )
                    categories.forEach { entry ->
                        FilterChip(
                            selected = current.categoryId == entry.category.id,
                            onClick = { viewModel.edit { it.copy(categoryId = entry.category.id) } },
                            label = { Text(entry.category.name) },
                        )
                    }
                    AssistChip(
                        onClick = { showNewCategory = true },
                        label = { Text(stringResource(R.string.action_add)) },
                    )
                }
            }

            Column {
                SectionHeader(stringResource(R.string.field_tags))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    current.tags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = { viewModel.removeTag(tag) },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.action_remove),
                                    modifier = Modifier.width(16.dp),
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text(stringResource(R.string.tag_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            viewModel.addTag(tagInput)
                            tagInput = ""
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions = remember(tagInput, current.tags) { viewModel.suggestions(tagInput) }
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            AssistChip(
                                onClick = {
                                    viewModel.addTag(suggestion)
                                    tagInput = ""
                                },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
                if (tagInput.isNotBlank() && suggestions.none { it.equals(tagInput.trim(), true) }) {
                    TextButton(
                        onClick = {
                            viewModel.addTag(tagInput)
                            tagInput = ""
                        },
                    ) { Text("${stringResource(R.string.action_create)}: ${tagInput.trim()}") }
                }
            }

            Column {
                SectionHeader(stringResource(R.string.field_status))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReadingStatus.entries.forEach { status ->
                        FilterChip(
                            selected = current.status == status,
                            onClick = { viewModel.edit { it.copy(status = status) } },
                            label = { Text(status.label()) },
                        )
                    }
                }
            }

            Text(
                text = "${stringResource(R.string.field_file_name)}: ${current.fileName}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showNewCategory) {
        TextInputDialog(
            title = stringResource(R.string.dialog_new_category),
            label = stringResource(R.string.dialog_name),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                viewModel.createCategory(name) { id -> viewModel.edit { it.copy(categoryId = id) } }
                showNewCategory = false
            },
            onDismiss = { showNewCategory = false },
        )
    }
}

@Composable
private fun EditField(
    label: String,
    value: String,
    importedValue: String?,
    onValueChange: (String) -> Unit,
    onReset: (String) -> Unit,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Editing never rewrites the file, and the file's own value stays one tap away.
        if (!importedValue.isNullOrBlank() && importedValue != value) {
            TextButton(onClick = { onReset(importedValue) }) {
                Text(
                    text = "${stringResource(R.string.reset_to_file_value)}: $importedValue",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
