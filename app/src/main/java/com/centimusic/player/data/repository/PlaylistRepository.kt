package com.centimusic.player.data.repository

import androidx.compose.runtime.Immutable
import com.centimusic.player.data.database.PlaylistDao
import com.centimusic.player.data.database.PlaylistEntity
import com.centimusic.player.data.media.Library
import com.centimusic.player.data.media.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    /** Tracks that still exist in the library, in playlist order. */
    val songs: List<Song>,
    /** Entries whose file is gone or inaccessible. Kept so they reappear if the file returns. */
    val missingCount: Int,
) {
    val durationMs: Long get() = songs.sumOf { it.durationMs }
}

class PlaylistRepository(
    private val dao: PlaylistDao,
    private val library: StateFlow<Library>,
) {
    val playlists: Flow<List<Playlist>> = combine(dao.observePlaylists(), dao.observeAllTracks(), library) { lists, tracks, lib ->
        val byPlaylist = tracks.groupBy { it.playlistId }
        lists.map { p -> p.toPlaylist(byPlaylist[p.id].orEmpty().map { it.mediaId }, lib) }
    }

    fun playlist(id: Long): Flow<Playlist?> = combine(dao.observePlaylist(id), dao.observeTracks(id), library) { p, tracks, lib ->
        p?.toPlaylist(tracks.map { it.mediaId }, lib)
    }

    private fun PlaylistEntity.toPlaylist(mediaIds: List<String>, lib: Library): Playlist {
        val songs = mediaIds.mapNotNull { lib.byId[it] }
        return Playlist(id, name, songs, mediaIds.size - songs.size)
    }

    suspend fun create(name: String, initial: List<String> = emptyList()): Long {
        val now = System.currentTimeMillis()
        val id = dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = now, updatedAt = now))
        if (initial.isNotEmpty()) dao.appendTracks(id, initial, now)
        return id
    }

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim(), System.currentTimeMillis())
    suspend fun delete(id: Long) = dao.deletePlaylist(id)
    suspend fun add(id: Long, mediaIds: List<String>): Int = dao.appendTracks(id, mediaIds, System.currentTimeMillis())

    /** Rewrites the playlist with [orderedIds], preserving entries for files that are currently missing. */
    suspend fun setOrder(id: Long, orderedIds: List<String>) {
        val missing = dao.tracks(id).map { it.mediaId }.filterNot { it in orderedIds }
        dao.replaceTracks(id, orderedIds + missing, System.currentTimeMillis())
    }

    suspend fun remove(id: Long, mediaId: String) {
        val remaining = dao.tracks(id).map { it.mediaId }.filterNot { it == mediaId }
        dao.replaceTracks(id, remaining, System.currentTimeMillis())
    }
}
