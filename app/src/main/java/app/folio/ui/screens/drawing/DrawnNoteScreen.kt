package app.folio.ui.screens.drawing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.reader.common.InkLayer
import app.folio.ui.components.ConfirmDialog
import app.folio.ui.folioViewModel
import app.folio.ui.screens.reader.ScribbleToolbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawnNoteScreen(
    noteId: Long,
    onBack: () -> Unit,
    onOpenInBook: (bookId: Long, location: String) -> Unit,
    viewModel: DrawnNoteViewModel = folioViewModel(key = "drawing-$noteId") { DrawnNoteViewModel(it, noteId) },
) {
    val note by viewModel.note.collectAsStateWithLifecycle()
    val bookTitle by viewModel.bookTitle.collectAsStateWithLifecycle()
    val strokes by viewModel.strokes.collectAsStateWithLifecycle()
    val scribble by viewModel.scribble.collectAsStateWithLifecycle()
    val undoRedo by viewModel.undoRedo.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    // Leave once the note is gone (deleted here or elsewhere).
    LaunchedEffect(note) {
        if (note != null) loaded = true else if (loaded) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = bookTitle ?: stringResource(R.string.drawing_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        note?.let {
                            Text(
                                text = it.positionLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    note?.let { current ->
                        IconButton(onClick = { onOpenInBook(current.bookId, current.location) }) {
                            Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = stringResource(R.string.drawing_open_in_book))
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val current = note
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (current != null) {
                    // A white card of fixed shape; ink is normalized against it.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .wrapContentSize(Alignment.Center)
                            .aspectRatio(1f / current.aspect)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                    ) {
                        InkLayer(
                            strokes = strokes,
                            scribble = scribble,
                            crop = null,
                            multiplyHighlighter = true,
                            onStroke = viewModel::stroke,
                            onErase = viewModel::erase,
                            onEraseEnd = viewModel::endErase,
                        )
                    }
                }
            }
            ScribbleToolbar(
                scribble = scribble,
                undoRedo = undoRedo,
                onTool = viewModel::selectTool,
                onStyle = viewModel::updateStyle,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onDone = onBack,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.drawing_delete_title),
            message = stringResource(R.string.drawing_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
