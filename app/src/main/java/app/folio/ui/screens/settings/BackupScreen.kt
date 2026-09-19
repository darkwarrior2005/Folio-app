package app.folio.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.components.SectionHeader
import app.folio.ui.folioViewModel
import app.folio.ui.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(
    onBack: () -> Unit,
    onReset: () -> Unit,
    viewModel: SettingsViewModel = folioViewModel { SettingsViewModel(it) },
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var includeFiles by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.backup(it.toString(), includeFiles) } }

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.restore(it.toString()) }
    }

    val exportNotes = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let { viewModel.exportAnnotations(it.toString()) } }

    SettingsScaffold(stringResource(R.string.settings_data), onBack) {
        SectionHeader(stringResource(R.string.backup_title))
        Text(
            text = stringResource(R.string.settings_offline_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        SwitchRow(stringResource(R.string.backup_include_files), includeFiles) { includeFiles = it }

        Button(
            onClick = {
                val stamp = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
                createBackup.launch("folio-backup-$stamp.zip")
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.backup_create)) }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { pickBackup.launch(arrayOf("application/zip", "*/*")) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.backup_restore)) }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = { exportNotes.launch("folio-notes.md") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_export_annotations)) }

        if (busy) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        message?.let { current ->
            Spacer(Modifier.height(16.dp))
            val text = when (current) {
                is SettingsMessage.BackupDone -> stringResource(R.string.backup_created) +
                    " · ${Format.fileSize(current.summary.bytes)}"

                is SettingsMessage.RestoreDone -> buildString {
                    append(pluralStringResource(R.plurals.backup_restored, current.summary.books, current.summary.books))
                    if (current.summary.missingFiles > 0) {
                        append(" · ")
                        append(pluralStringResource(R.plurals.backup_missing_files, current.summary.missingFiles, current.summary.missingFiles))
                    }
                }

                is SettingsMessage.Exported -> stringResource(R.string.action_export_notes)
                SettingsMessage.Failed -> stringResource(R.string.backup_failed)
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = viewModel::clearMessage) { Text(stringResource(R.string.action_close)) }
        }

        Spacer(Modifier.height(28.dp))
        TextButton(onClick = { confirmReset = true }) {
            Text(
                text = stringResource(R.string.settings_reset),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset),
            message = stringResource(R.string.dialog_remove_from_library_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmReset = false
                viewModel.resetAppData(onReset)
            },
            onDismiss = { confirmReset = false },
        )
    }
}
