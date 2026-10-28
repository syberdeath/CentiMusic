package com.centimusic.player.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_tracks ORDER BY playlistId, position")
    fun observeAllTracks(): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    fun observeTracks(id: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :id ORDER BY position")
    suspend fun tracks(id: Long): List<PlaylistTrackEntity>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert
    suspend fun insertTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :id")
    suspend fun clearTracks(id: Long)

    @Query("DELETE FROM playlist_tracks WHERE mediaId = :mediaId")
    suspend fun deleteTracksFor(mediaId: String)

    @Transaction
    suspend fun replaceTracks(id: Long, mediaIds: List<String>, now: Long) {
        clearTracks(id)
        insertTracks(mediaIds.mapIndexed { i, mediaId -> PlaylistTrackEntity(playlistId = id, mediaId = mediaId, position = i) })
        touch(id, now)
    }

    @Transaction
    suspend fun appendTracks(id: Long, mediaIds: List<String>, now: Long): Int {
        val existing = tracks(id)
        val present = existing.mapTo(HashSet()) { it.mediaId }
        val fresh = mediaIds.distinct().filterNot { it in present }
        val start = (existing.maxOfOrNull { it.position } ?: -1) + 1
        insertTracks(fresh.mapIndexed { i, mediaId -> PlaylistTrackEntity(playlistId = id, mediaId = mediaId, position = start + i) })
        touch(id, now)
        return fresh.size
    }
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY likedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE mediaId = :mediaId)")
    suspend fun isFavorite(mediaId: String): Boolean
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 12")
    fun observeSearches(): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsertSearch(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearch(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearches()

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT 50")
    fun observeRecentlyPlayed(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history")
    fun observeAllPlays(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE playCount >= 2 ORDER BY playCount DESC, playedAt DESC LIMIT 30")
    fun observeMostPlayed(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE mediaId = :mediaId")
    suspend fun playEntry(mediaId: String): PlayHistoryEntity?

    @Upsert
    suspend fun upsertPlay(entry: PlayHistoryEntity)

    @Query("DELETE FROM play_history WHERE mediaId = :mediaId")
    suspend fun deletePlay(mediaId: String)

    @Insert
    suspend fun insertListen(entry: ListenEntity)

    @Query("SELECT * FROM listens WHERE playedAt >= :since")
    fun observeListensSince(since: Long): Flow<List<ListenEntity>>

    @Query("DELETE FROM listens WHERE playedAt < :before")
    suspend fun pruneListens(before: Long)
}

@Dao
interface ImportedTrackDao {
    @Query("SELECT * FROM imported_tracks ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ImportedTrackEntity>>

    @Upsert
    suspend fun upsert(tracks: List<ImportedTrackEntity>)

    @Query("DELETE FROM imported_tracks WHERE uri IN (:uris)")
    suspend fun delete(uris: List<String>)
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM saved_queue ORDER BY position")
    suspend fun load(): List<SavedQueueEntity>

    @Query("DELETE FROM saved_queue")
    suspend fun clear()

    @Insert
    suspend fun insert(items: List<SavedQueueEntity>)

    @Transaction
    suspend fun replace(mediaIds: List<String>) {
        clear()
        insert(mediaIds.mapIndexed { i, id -> SavedQueueEntity(i, id) })
    }
}

@Dao
interface SongEditDao {
    @Query("SELECT * FROM song_edits")
    fun observeAll(): Flow<List<SongEditEntity>>

    @Query("SELECT * FROM song_edits WHERE mediaId = :mediaId")
    suspend fun get(mediaId: String): SongEditEntity?

    @Upsert
    suspend fun upsert(edit: SongEditEntity)

    @Query("DELETE FROM song_edits WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: String)
}
