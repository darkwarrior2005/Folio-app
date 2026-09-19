package app.folio.core.model

import androidx.room.ColumnInfo
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** One set of track metadata; a track keeps an imported set and a user-override set. */
@Serializable
data class TrackMetadata(
    @ColumnInfo(name = "title") val title: String? = null,
    @ColumnInfo(name = "artist") val artist: String? = null,
    @ColumnInfo(name = "album") val album: String? = null,
    @ColumnInfo(name = "trackNumber") val trackNumber: Int? = null,
    @ColumnInfo(name = "year") val year: Int? = null,
)

/** Same rule as books: the user's value wins, the audio file itself is never edited. */
object TrackMetadataResolver {
    fun resolve(imported: TrackMetadata, overrides: TrackMetadata): TrackMetadata = TrackMetadata(
        title = overrides.title ?: imported.title,
        artist = overrides.artist ?: imported.artist,
        album = overrides.album ?: imported.album,
        trackNumber = overrides.trackNumber ?: imported.trackNumber,
        year = overrides.year ?: imported.year,
    )

    fun displayTitle(imported: TrackMetadata, overrides: TrackMetadata, fileName: String): String =
        overrides.title?.takeIf { it.isNotBlank() }
            ?: imported.title?.takeIf { it.isNotBlank() }
            ?: MetadataResolver.titleFromFileName(fileName)

    fun displayArtist(imported: TrackMetadata, overrides: TrackMetadata): String? =
        (overrides.artist ?: imported.artist)?.takeIf { it.isNotBlank() }
}

enum class BookMusicMode { LOOP, SHUFFLE, SELECTION }

/** One attached source of a book's music: a whole collection or a single track. */
data class MusicSourceRef(val collectionId: Long?, val trackId: Long?)

object MusicQueueBuilder {

    /** Tracks attached to a book, in source order, each once, skipping tracks that are unavailable. */
    fun attachedTracks(
        sources: List<MusicSourceRef>,
        collectionTracks: Map<Long, List<Long>>,
        available: Set<Long>,
    ): List<Long> {
        val out = LinkedHashSet<Long>()
        sources.forEach { source ->
            when {
                source.collectionId != null -> collectionTracks[source.collectionId].orEmpty().forEach { out += it }
                source.trackId != null -> out += source.trackId
            }
        }
        return out.filter { it in available }
    }

    fun forBook(mode: BookMusicMode, attached: List<Long>, selection: List<Long>, seed: Long): List<Long> =
        when (mode) {
            BookMusicMode.LOOP -> attached
            BookMusicMode.SHUFFLE -> attached.shuffled(Random(seed))
            BookMusicMode.SELECTION -> {
                val allowed = attached.toSet()
                selection.filter { it in allowed }.distinct()
            }
        }
}
