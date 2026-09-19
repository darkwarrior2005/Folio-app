package app.folio.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.folio.AppContainer
import app.folio.core.model.BookMetadata
import app.folio.core.model.MetadataResolver
import app.folio.core.model.ReadingStatus
import app.folio.core.model.TagNormalizer
import app.folio.data.db.CategoryWithCount
import app.folio.data.db.TagEntity
import app.folio.data.repo.BookEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditForm(
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val series: String = "",
    val volume: String = "",
    val year: String = "",
    val language: String = "",
    val publisher: String = "",
    val categoryId: Long? = null,
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val tags: List<String> = emptyList(),
    val fileName: String = "",
)

class EditBookViewModel(
    private val container: AppContainer,
    private val bookId: Long,
) : ViewModel() {

    private val _form = MutableStateFlow<EditForm?>(null)
    val form: StateFlow<EditForm?> = _form.asStateFlow()

    /** The values read from the file, so each field can be reset to them. */
    private val _imported = MutableStateFlow(BookMetadata())
    val imported: StateFlow<BookMetadata> = _imported.asStateFlow()

    val categories: StateFlow<List<CategoryWithCount>> = container.organization.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags: StateFlow<List<TagEntity>> = container.organization.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val entity = container.library.entity(bookId) ?: return@launch
            val resolved = MetadataResolver.resolve(entity.imported, entity.overrides)
            _imported.value = entity.imported
            _form.value = EditForm(
                title = entity.displayTitle,
                author = resolved.author.orEmpty(),
                description = resolved.description.orEmpty(),
                series = resolved.series.orEmpty(),
                volume = resolved.volume?.let { formatVolume(it) }.orEmpty(),
                year = resolved.year?.toString().orEmpty(),
                language = resolved.language.orEmpty(),
                publisher = resolved.publisher.orEmpty(),
                categoryId = entity.categoryId,
                status = entity.status,
                tags = container.organization.currentTagIds(bookId).let { ids ->
                    container.organization.allTagsOnce().filter { it.id in ids }.map { it.name }
                },
                fileName = entity.fileName,
            )
        }
    }

    fun edit(transform: (EditForm) -> EditForm) {
        _form.value = _form.value?.let(transform)
    }

    fun addTag(raw: String) {
        val clean = TagNormalizer.clean(raw)
        if (clean.isBlank()) return
        val current = _form.value ?: return
        if (current.tags.any { TagNormalizer.key(it) == TagNormalizer.key(clean) }) return
        _form.value = current.copy(tags = current.tags + clean)
    }

    fun removeTag(name: String) {
        val current = _form.value ?: return
        _form.value = current.copy(tags = current.tags.filterNot { it == name })
    }

    fun suggestions(query: String): List<String> {
        val current = _form.value ?: return emptyList()
        val applied = current.tags.map { TagNormalizer.key(it) }.toSet()
        return TagNormalizer.suggest(query, allTags.value, { it.name }, applied).map { it.name }
    }

    fun createCategory(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        container.organization.ensureCategory(name)?.let(onCreated)
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        val current = _form.value ?: return@launch
        val tagIds = container.organization.ensureTags(current.tags)
        container.library.applyEdit(
            bookId = bookId,
            edit = BookEdit(
                title = current.title.trim().ifBlank { null },
                author = current.author.trim().ifBlank { null },
                description = current.description.trim().ifBlank { null },
                series = current.series.trim().ifBlank { null },
                volume = current.volume.trim().toDoubleOrNull(),
                year = current.year.trim().toIntOrNull(),
                language = current.language.trim().ifBlank { null },
                publisher = current.publisher.trim().ifBlank { null },
                categoryId = current.categoryId,
                status = current.status,
                tagNames = current.tags,
            ),
            tagIds = tagIds,
        )
        onSaved()
    }

    private fun formatVolume(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
}
