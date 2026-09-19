package app.folio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // Tracks
    @Insert suspend fun insertTrack(track: TrackEntity): Long
    @Update suspend fun updateTrack(track: TrackEntity)
    @Query("SELECT * FROM tracks ORDER BY sortTitle") fun observeTracks(): Flow<List<TrackEntity>>
    @Query("SELECT * FROM tracks ORDER BY sortTitle") suspend fun allTracks(): List<TrackEntity>
    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun track(id: Long): TrackEntity?
    @Query("SELECT * FROM tracks WHERE id = :id") fun observeTrack(id: Long): Flow<TrackEntity?>
    @Query("SELECT * FROM tracks WHERE id IN (:ids)") suspend fun tracks(ids: List<Long>): List<TrackEntity>
    @Query("SELECT * FROM tracks WHERE fileHash = :hash AND fileSize = :size LIMIT 1")
    suspend fun findDuplicate(hash: String, size: Long): TrackEntity?
    @Query("UPDATE tracks SET missing = :missing WHERE id = :id") suspend fun setMissing(id: Long, missing: Boolean)
    @Query("DELETE FROM tracks WHERE id IN (:ids)") suspend fun deleteTracks(ids: List<Long>)
    @Query("DELETE FROM tracks") suspend fun deleteAllTracks()

    // Tags
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTag(tag: MusicTagEntity): Long
    @Query("SELECT * FROM music_tags WHERE nameKey = :key") suspend fun tagByKey(key: String): MusicTagEntity?
    @Query("SELECT * FROM music_tags ORDER BY nameKey") suspend fun allTags(): List<MusicTagEntity>
    @Query(
        """SELECT t.*, (SELECT COUNT(*) FROM track_tags tt WHERE tt.tagId = t.id) AS trackCount
           FROM music_tags t ORDER BY t.nameKey""",
    )
    fun observeTagsWithCounts(): Flow<List<MusicTagWithCount>>
    @Query("SELECT * FROM track_tags") fun observeTrackTags(): Flow<List<TrackTagEntity>>
    @Query("SELECT * FROM track_tags") suspend fun allTrackTags(): List<TrackTagEntity>
    @Query("SELECT t.* FROM music_tags t JOIN track_tags tt ON tt.tagId = t.id WHERE tt.trackId = :trackId ORDER BY t.nameKey")
    fun observeTagsForTrack(trackId: Long): Flow<List<MusicTagEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun linkTags(links: List<TrackTagEntity>)
    @Query("DELETE FROM track_tags WHERE trackId = :trackId") suspend fun clearTrackTags(trackId: Long)
    @Query("DELETE FROM music_tags WHERE id NOT IN (SELECT tagId FROM track_tags)") suspend fun deleteUnusedTags()
    @Query("DELETE FROM music_tags") suspend fun deleteAllTags()

    // Collections
    @Insert suspend fun insertCollection(collection: MusicCollectionEntity): Long
    @Query("UPDATE music_collections SET name = :name WHERE id = :id") suspend fun renameCollection(id: Long, name: String)
    @Query("DELETE FROM music_collections WHERE id = :id") suspend fun deleteCollection(id: Long)
    @Query("SELECT * FROM music_collections ORDER BY sortOrder, name") suspend fun allCollections(): List<MusicCollectionEntity>
    @Query(
        """SELECT c.*, (SELECT COUNT(*) FROM music_collection_tracks ct WHERE ct.collectionId = c.id) AS trackCount
           FROM music_collections c ORDER BY c.sortOrder, c.name""",
    )
    fun observeCollections(): Flow<List<MusicCollectionWithCount>>
    @Query("SELECT * FROM music_collections WHERE id = :id") fun observeCollection(id: Long): Flow<MusicCollectionEntity?>
    @Query(
        """SELECT t.* FROM tracks t JOIN music_collection_tracks ct ON ct.trackId = t.id
           WHERE ct.collectionId = :collectionId ORDER BY ct.position""",
    )
    fun observeCollectionTracks(collectionId: Long): Flow<List<TrackEntity>>
    @Query("SELECT * FROM music_collection_tracks ORDER BY collectionId, position") suspend fun allCollectionLinks(): List<MusicCollectionTrackEntity>
    @Query("SELECT COALESCE(MAX(position), -1) FROM music_collection_tracks WHERE collectionId = :collectionId")
    suspend fun lastPosition(collectionId: Long): Int
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addToCollection(links: List<MusicCollectionTrackEntity>)
    @Query("DELETE FROM music_collection_tracks WHERE collectionId = :collectionId AND trackId IN (:trackIds)")
    suspend fun removeFromCollection(collectionId: Long, trackIds: List<Long>)
    @Upsert suspend fun upsertCollectionLinks(links: List<MusicCollectionTrackEntity>)
    @Query("DELETE FROM music_collections") suspend fun deleteAllCollections()

    // Book links
    @Upsert suspend fun upsertBookMusic(entity: BookMusicEntity)
    @Query("SELECT * FROM book_music WHERE bookId = :bookId") suspend fun bookMusic(bookId: Long): BookMusicEntity?
    @Query("SELECT * FROM book_music WHERE bookId = :bookId") fun observeBookMusic(bookId: Long): Flow<BookMusicEntity?>
    @Query("SELECT * FROM book_music_sources WHERE bookId = :bookId ORDER BY position") suspend fun sources(bookId: Long): List<BookMusicSourceEntity>
    @Query("SELECT * FROM book_music_sources WHERE bookId = :bookId ORDER BY position") fun observeSources(bookId: Long): Flow<List<BookMusicSourceEntity>>
    @Query("DELETE FROM book_music_sources WHERE bookId = :bookId") suspend fun clearSources(bookId: Long)
    @Insert suspend fun insertSources(sources: List<BookMusicSourceEntity>)
    @Query("SELECT * FROM book_music_selection WHERE bookId = :bookId ORDER BY position") suspend fun selection(bookId: Long): List<BookMusicSelectionEntity>
    @Query("SELECT * FROM book_music_selection WHERE bookId = :bookId ORDER BY position") fun observeSelection(bookId: Long): Flow<List<BookMusicSelectionEntity>>
    @Query("DELETE FROM book_music_selection WHERE bookId = :bookId") suspend fun clearSelection(bookId: Long)
    @Insert suspend fun insertSelection(rows: List<BookMusicSelectionEntity>)
    @Query("DELETE FROM book_music WHERE bookId = :bookId") suspend fun deleteBookMusic(bookId: Long)
    @Query("SELECT * FROM book_music") suspend fun allBookMusic(): List<BookMusicEntity>
    @Query("SELECT * FROM book_music_sources") suspend fun allSources(): List<BookMusicSourceEntity>
    @Query("SELECT * FROM book_music_selection") suspend fun allSelections(): List<BookMusicSelectionEntity>
    @Query("DELETE FROM book_music") suspend fun deleteAllBookMusic()
}
