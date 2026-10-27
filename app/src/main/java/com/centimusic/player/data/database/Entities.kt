package com.centimusic.player.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_tracks",
    indices = [Index("playlistId")],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val playlistId: Long,
    val mediaId: String,
    val position: Int,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val mediaId: String,
    val likedAt: Long,
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val mediaId: String,
    val playedAt: Long,
    val playCount: Int,
)

/** Files the user picked through the system file picker (persisted URI permission held). */
@Entity(tableName = "imported_tracks")
data class ImportedTrackEntity(
    @PrimaryKey val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val addedAt: Long,
)

/** The last playback queue, restored on the next launch or by system media resumption. */
@Entity(tableName = "saved_queue")
data class SavedQueueEntity(
    @PrimaryKey val position: Int,
    val mediaId: String,
)

/** User edits to a song's info, applied on top of the file's tags inside CentiMusic only. */
@Entity(tableName = "song_edits")
data class SongEditEntity(
    @PrimaryKey val mediaId: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    /** Custom cover stored in the app's private files. */
    val artworkPath: String?,
    val updatedAt: Long,
)
