package app.folio.ui.screens.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.folio.R
import app.folio.core.model.TagNormalizer
import app.folio.data.repo.TrackEdit
import app.folio.ui.LocalContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun TrackEditScreen(trackId: Long, onBack: () -> Unit) {
    val container = LocalContainer.current
    val music = container.music
    val scope = rememberCoroutineScope()
    val allTags by music.tags.collectAsStateWithLifecycle(emptyList())

    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    val tagNames = remember { mutableStateListOf<String>() }
    var tagInput by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var removal by remember { mutableStateOf<TrackRemoval?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(trackId) {
        val track = music.observeTrack(trackId).first() ?: return@LaunchedEffect
        val resolved = app.folio.core.model.TrackMetadataResolver.resolve(track.imported, track.overrides)
        title = track.displayTitle
        artist = resolved.artist.orEmpty()
        album = resolved.album.orEmpty()
        year = resolved.year?.toString().orEmpty()
        fileName = track.fileName
        tagNames.clear()
        tagNames += music.observeTagsForTrack(trackId).first().map { it.name }
    }

    fun addTag(raw: String) {
        val clean = TagNormalizer.clean(raw)
        if (clean.isNotBlank() && tagNames.none { TagNormalizer.key(it) == TagNormalizer.key(clean) }) tagNames += clean
        tagInput = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.music_edit_track)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (tagInput.isNotBlank()) addTag(tagInput)
                            scope.launch {
                                music.applyEdit(
                                    trackId,
                                    TrackEdit(title, artist, album, year.toIntOrNull(), tagNames.toList()),
                                )
                                onBack()
                            }
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(artist, { artist = it }, label = { Text(stringResource(R.string.music_field_artist)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(album, { album = it }, label = { Text(stringResource(R.string.music_field_album)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                year,
                { value -> year = value.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.music_field_year)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.field_tags))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tagNames.forEach { name ->
                    InputChip(
                        selected = false,
                        onClick = { tagNames.remove(name) },
                        label = { Text(name) },
                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_remove)) },
                    )
                }
            }
            OutlinedTextField(
                tagInput,
                { tagInput = it },
                label = { Text(stringResource(R.string.tag_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTag(tagInput) }),
                modifier = Modifier.fillMaxWidth(),
            )
            val suggestions = TagNormalizer.suggest(
                query = tagInput,
                candidates = allTags.map { it.tag.name },
                name = { it },
                exclude = tagNames.map(TagNormalizer::key).toSet(),
                limit = 6,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    SuggestionChip(onClick = { addTag(suggestion) }, label = { Text(suggestion) })
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.music_file_name, fileName))
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = { removal = TrackRemoval.LIBRARY_ONLY }) {
                Text(stringResource(R.string.music_remove_from_library))
            }
            TextButton(onClick = { removal = TrackRemoval.DELETE_FILE }) {
                Text(stringResource(R.string.music_delete_file), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    val current by music.observeTrack(trackId).collectAsStateWithLifecycle(null)
    val pending = removal
    val track = current
    if (pending != null && track != null) {
        TrackRemovalDialog(
            track = track,
            removal = pending,
            onDismiss = { removal = null },
            onFinished = { message, removed ->
                android.widget.Toast.makeText(context, context.getString(message), android.widget.Toast.LENGTH_SHORT).show()
                if (removed) onBack()
            },
        )
    }
}
