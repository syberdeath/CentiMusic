package com.centimusic.player.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.centimusic.player.data.database.ImportedTrackEntity

/** Reads tags from files chosen through the system file picker. */
class ImportedTrackReader(private val context: Context) {

    fun read(uri: Uri): ImportedTrackEntity? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            if (hasAudio != null && hasAudio != "yes") return null
            ImportedTrackEntity(
                uri = uri.toString(),
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).cleanTag()
                    ?: displayName(uri)?.substringBeforeLast('.')
                    ?: "Unknown title",
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).cleanTag()
                    ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).cleanTag()
                    ?: MediaStoreScanner.UNKNOWN_ARTIST,
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).cleanTag()
                    ?: MediaStoreScanner.UNKNOWN_ALBUM,
                durationMs = duration,
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0,
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull() ?: 0,
                addedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            Log.w("ImportedTrackReader", "Unreadable file $uri", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

fun ImportedTrackEntity.toSong(): Song {
    return Song(
        id = uri,
        title = title,
        artist = artist,
        album = album,
        albumId = 0,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        dateAddedSec = addedAt / 1000,
        folder = "Imported files",
        source = SongSource.IMPORTED,
    )
}
