package com.centimusic.player.playback

import android.net.Uri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.centimusic.player.data.media.ArtworkLoader
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.SongSource

internal const val EXTRA_DURATION = "nm.duration"
internal const val EXTRA_ALBUM_ID = "nm.album_id"
internal const val EXTRA_SOURCE = "nm.source"
internal const val EXTRA_MEDIA_ID = "nm.media_id"

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id)
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(artist)
            .setTrackNumber(trackNumber.takeIf { it > 0 })
            .setArtworkUri(if (source == SongSource.MEDIA_STORE && albumId > 0) ArtworkLoader.albumArtUri(albumId) else null)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(bundleOf(EXTRA_DURATION to durationMs, EXTRA_ALBUM_ID to albumId, EXTRA_SOURCE to source.name, EXTRA_MEDIA_ID to id))
            .build(),
    )
    .build()

/** Rebuilds a Song from queue metadata when the track isn't (or is no longer) in the scanned library. */
fun MediaItem.toSong(): Song {
    val extras = mediaMetadata.extras
    return Song(
        id = mediaId,
        title = mediaMetadata.title?.toString() ?: Uri.parse(mediaId).lastPathSegment ?: "Unknown title",
        artist = mediaMetadata.artist?.toString() ?: "Unknown artist",
        album = mediaMetadata.albumTitle?.toString() ?: "Unknown album",
        albumId = extras?.getLong(EXTRA_ALBUM_ID) ?: 0L,
        durationMs = extras?.getLong(EXTRA_DURATION) ?: 0L,
        trackNumber = mediaMetadata.trackNumber ?: 0,
        year = 0,
        dateAddedSec = 0,
        folder = "",
        source = extras?.getString(EXTRA_SOURCE)?.let { runCatching { SongSource.valueOf(it) }.getOrNull() } ?: SongSource.IMPORTED,
    )
}

/** Controllers may send items without a playback URI; ours always use the content URI as media id. */
fun MediaItem.withPlayableUri(): MediaItem =
    if (localConfiguration != null) this else buildUpon().setUri(Uri.parse(mediaId)).build()
