package app.folio.data.repo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.withTransaction
import app.folio.core.model.BookMusicMode
import app.folio.core.model.MetadataResolver
import app.folio.core.model.MusicQueueBuilder
import app.folio.core.model.MusicSourceRef
import app.folio.core.model.TagNormalizer
import app.folio.core.model.TrackMetadata
import app.folio.core.model.TrackMetadataResolver
import app.folio.data.db.BookMusicEntity
import app.folio.data.db.BookMusicSelectionEntity
import app.folio.data.db.BookMusicSourceEntity
import app.folio.data.db.FolioDatabase
import app.folio.data.db.MusicCollectionEntity
import app.folio.data.db.MusicCollectionTrackEntity
import app.folio.data.db.MusicTagEntity
import app.folio.data.db.TrackEntity
import app.folio.data.db.TrackTagEntity
import app.folio.data.files.CacheStore
import app.folio.data.files.FileAccess
import app.folio.data.files.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.util.Locale

data class TrackEdit(
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: Int?,
    val tagNames: List<String>,
)

data class NewTrack(
    val uri: String,
    val fileName: String,
    val fileSize: Long,
    val fileHash: String,
    val mimeType: String?,
    val durationMs: Long,
    val imported: TrackMetadata,
    val cover: Bitmap?,
)

data class BookMusicSettings(
    val mode: BookMusicMode,
    val autoplay: Boolean,
    val sources: List<BookMusicSourceEntity>,
    val selection: List<Long>,
)

sealed interface MusicImportResult {
    data class Added(val trackId: Long) : MusicImportResult
    data class Duplicate(val existingId: Long) : MusicImportResult
    data object Unreadable : MusicImportResult
}

/** The music library. Audio files stay where the user keeps them and are never modified. */
class MusicRepository(
    private val db: FolioDatabase,
    private val files: FileAccess,
    private val cache: CacheStore,
) {
    private val dao = db.music()

    val tracks: Flow<List<TrackEntity>> = dao.observeTracks()
    val collections = dao.observeCollections()
    val tags = dao.observeTagsWithCounts()
    val trackTags: Flow<List<TrackTagEntity>> = dao.observeTrackTags()

    fun observeTrack(id: Long) = dao.observeTrack(id)
    fun observeTagsForTrack(id: Long) = dao.observeTagsForTrack(id)
    fun observeCollection(id: Long) = dao.observeCollection(id)
    fun observeCollectionTracks(id: Long) = dao.observeCollectionTracks(id)

    fun observeBookMusic(bookId: Long): Flow<BookMusicSettings?> = combine(
        dao.observeBookMusic(bookId),
        dao.observeSources(bookId),
        dao.observeSelection(bookId),
    ) { music, sources, selection ->
        music?.let { BookMusicSettings(it.mode, it.autoplay, sources, selection.map { row -> row.trackId }) }
    }

    suspend fun importUri(uri: String): MusicImportResult = withContext(Dispatchers.IO) {
        val info = files.info(uri) ?: return@withContext MusicImportResult.Unreadable
        if (!isAudio(info.name, info.mimeType)) return@withContext MusicImportResult.Unreadable
        // Ask for write access too, so "Delete file from phone" can work later; some providers
        // only grant read access, and then deleting is refused with a message instead.
        val granted = files.uriOf(uri)
        files.takePersistablePermission(granted, write = true) || files.takePersistablePermission(granted)
        val hash = files.openInput(uri)?.use { Hashing.quickHash(it, info.size) }
            ?: return@withContext MusicImportResult.Unreadable
        dao.findDuplicate(hash, info.size)?.let { return@withContext MusicImportResult.Duplicate(it.id) }
        val extracted = extract(uri) ?: return@withContext MusicImportResult.Unreadable
        val id = insert(
            NewTrack(uri, info.name, info.size, hash, info.mimeType, extracted.durationMs, extracted.metadata, extracted.cover),
        )
        MusicImportResult.Added(id)
    }

    suspend fun importFolder(treeUri: String): List<MusicImportResult> = withContext(Dispatchers.IO) {
        files.takePersistablePermission(files.uriOf(treeUri))
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(files.context, Uri.parse(treeUri))
            ?: return@withContext emptyList()
        val uris = mutableListOf<String>()
        fun walk(dir: androidx.documentfile.provider.DocumentFile, depth: Int) {
            if (depth > MAX_FOLDER_DEPTH) return
            dir.listFiles().forEach { child ->
                when {
                    child.isDirectory -> walk(child, depth + 1)
                    isAudio(child.name.orEmpty(), child.type) -> uris += child.uri.toString()
                }
            }
        }
        walk(root, 0)
        uris.map { importUri(it) }
    }

    suspend fun insert(track: NewTrack): Long = db.withTransaction {
        val title = TrackMetadataResolver.displayTitle(track.imported, TrackMetadata(), track.fileName)
        val id = dao.insertTrack(
            TrackEntity(
                uri = track.uri,
                fileName = track.fileName,
                fileSize = track.fileSize,
                fileHash = track.fileHash,
                mimeType = track.mimeType,
                durationMs = track.durationMs,
                imported = track.imported,
                displayTitle = title,
                displayArtist = TrackMetadataResolver.displayArtist(track.imported, TrackMetadata()),
                sortTitle = title.lowercase(Locale.ROOT),
                addedAt = System.currentTimeMillis(),
            ),
        )
        track.cover?.let { bitmap ->
            cache.writeTrackCover(id, bitmap)?.let { path -> dao.updateTrack(dao.track(id)!!.copy(coverPath = path)) }
        }
        id
    }

    suspend fun applyEdit(trackId: Long, edit: TrackEdit) = db.withTransaction {
        val current = dao.track(trackId) ?: return@withTransaction
        val overrides = TrackMetadata(
            title = MetadataResolver.overrideFor(edit.title?.trim(), current.imported.title),
            artist = MetadataResolver.overrideFor(edit.artist?.trim(), current.imported.artist),
            album = MetadataResolver.overrideFor(edit.album?.trim(), current.imported.album),
            trackNumber = current.overrides.trackNumber,
            year = MetadataResolver.overrideFor(edit.year, current.imported.year),
        )
        val title = TrackMetadataResolver.displayTitle(current.imported, overrides, current.fileName)
        dao.updateTrack(
            current.copy(
                overrides = overrides,
                displayTitle = title,
                displayArtist = TrackMetadataResolver.displayArtist(current.imported, overrides),
                sortTitle = title.lowercase(Locale.ROOT),
            ),
        )
        dao.clearTrackTags(trackId)
        val tagIds = edit.tagNames.mapNotNull { ensureTag(it) }.distinct()
        dao.linkTags(tagIds.map { TrackTagEntity(trackId, it) })
        dao.deleteUnusedTags()
    }

    suspend fun removeFromLibrary(trackIds: List<Long>) {
        dao.deleteTracks(trackIds)
        trackIds.forEach { cache.trackCoverFile(it).delete() }
        dao.deleteUnusedTags()
    }

    /**
     * Explicit "Delete file from phone": deletes the audio file, then forgets the track. If the
     * file cannot be deleted (no write access), nothing is removed and false is returned.
     */
    suspend fun deleteFileAndForget(trackId: Long): Boolean {
        val track = dao.track(trackId) ?: return false
        val deleted = withContext(Dispatchers.IO) { files.deleteFile(track.uri) }
        if (deleted) removeFromLibrary(listOf(trackId))
        return deleted
    }

    suspend fun createCollection(name: String): Long =
        dao.insertCollection(MusicCollectionEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun renameCollection(id: Long, name: String) = dao.renameCollection(id, name.trim())

    suspend fun deleteCollection(id: Long) = dao.deleteCollection(id)

    suspend fun addToCollection(collectionId: Long, trackIds: List<Long>) = db.withTransaction {
        var position = dao.lastPosition(collectionId)
        dao.addToCollection(trackIds.map { MusicCollectionTrackEntity(collectionId, it, ++position) })
    }

    suspend fun removeFromCollection(collectionId: Long, trackIds: List<Long>) =
        dao.removeFromCollection(collectionId, trackIds)

    suspend fun reorderCollection(collectionId: Long, orderedTrackIds: List<Long>) =
        dao.upsertCollectionLinks(orderedTrackIds.mapIndexed { index, id -> MusicCollectionTrackEntity(collectionId, id, index) })

    suspend fun saveBookMusic(bookId: Long, settings: BookMusicSettings) = db.withTransaction {
        dao.upsertBookMusic(BookMusicEntity(bookId, settings.mode, settings.autoplay, System.currentTimeMillis()))
        dao.clearSources(bookId)
        dao.insertSources(settings.sources.mapIndexed { index, source -> source.copy(id = 0, bookId = bookId, position = index) })
        dao.clearSelection(bookId)
        dao.insertSelection(settings.selection.mapIndexed { index, id -> BookMusicSelectionEntity(bookId, id, index) })
    }

    suspend fun clearBookMusic(bookId: Long) = db.withTransaction {
        dao.clearSources(bookId)
        dao.clearSelection(bookId)
        dao.deleteBookMusic(bookId)
    }

    suspend fun hasMusic(bookId: Long): Boolean = dao.bookMusic(bookId) != null && dao.sources(bookId).isNotEmpty()

    suspend fun bookMusicSettings(bookId: Long): BookMusicSettings? {
        val music = dao.bookMusic(bookId) ?: return null
        return BookMusicSettings(music.mode, music.autoplay, dao.sources(bookId), dao.selection(bookId).map { it.trackId })
    }

    /** The tracks to play for a book now; files that disappeared are marked missing and skipped. */
    suspend fun resolveBookQueue(bookId: Long, seed: Long): List<TrackEntity> = withContext(Dispatchers.IO) {
        val settings = bookMusicSettings(bookId) ?: return@withContext emptyList()
        val collectionTracks = dao.allCollectionLinks().groupBy({ it.collectionId }, { it.trackId })
        val all = dao.allTracks().associateBy { it.id }
        val available = all.values.filter { track ->
            val present = files.exists(track.uri)
            if (present == track.missing) dao.setMissing(track.id, !present)
            present
        }.map { it.id }.toSet()
        val attached = MusicQueueBuilder.attachedTracks(
            settings.sources.map { MusicSourceRef(it.collectionId, it.trackId) },
            collectionTracks,
            available,
        )
        MusicQueueBuilder.forBook(settings.mode, attached, settings.selection, seed).mapNotNull { all[it] }
    }

    suspend fun markMissing(trackId: Long) = dao.setMissing(trackId, true)

    suspend fun tracksForIds(ids: List<Long>): List<TrackEntity> {
        val byId = dao.tracks(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    private suspend fun ensureTag(name: String): Long? {
        val clean = TagNormalizer.clean(name)
        if (clean.isBlank()) return null
        val key = TagNormalizer.key(clean)
        dao.tagByKey(key)?.let { return it.id }
        val id = dao.insertTag(MusicTagEntity(name = clean, nameKey = key, createdAt = System.currentTimeMillis()))
        return if (id > 0) id else dao.tagByKey(key)?.id
    }

    private data class Extracted(val metadata: TrackMetadata, val durationMs: Long, val cover: Bitmap?)

    private fun extract(uri: String): Extracted? {
        val descriptor = files.openFileDescriptor(uri) ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(descriptor.fileDescriptor)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: return null
            val metadata = TrackMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim()?.takeIf { it.isNotBlank() },
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim()?.takeIf { it.isNotBlank() },
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim()?.takeIf { it.isNotBlank() },
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull(),
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull(),
            )
            val cover = retriever.embeddedPicture?.let { bytes ->
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
            Extracted(metadata, duration, cover)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
            descriptor.close()
        }
    }

    companion object {
        private const val MAX_FOLDER_DEPTH = 6
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav")

        fun isAudio(name: String, mimeType: String?): Boolean =
            name.substringAfterLast('.', "").lowercase(Locale.ROOT) in AUDIO_EXTENSIONS ||
                mimeType?.startsWith("audio/") == true
    }
}
