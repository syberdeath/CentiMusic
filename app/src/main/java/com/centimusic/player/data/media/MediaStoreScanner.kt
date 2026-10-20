package com.centimusic.player.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File

/** Reads audio tracks from MediaStore. Requires the audio read permission. */
class MediaStoreScanner(private val resolver: ContentResolver) {

    fun scan(minDurationMs: Long): List<Song> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
        }.toTypedArray()
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val args = arrayOf(minDurationMs.toString())

        val songs = ArrayList<Song>()
        try {
            resolver.query(collection, projection, selection, args, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")
                ?.use { cursor -> while (cursor.moveToNext()) songs += cursor.toSong(collection) }
        } catch (e: SecurityException) {
            Log.w(TAG, "MediaStore query denied", e)
            throw e
        }
        return songs
    }

    private fun Cursor.toSong(collection: android.net.Uri): Song {
        val id = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        val uri = ContentUris.withAppendedId(collection, id)
        val displayName = string(MediaStore.Audio.Media.DISPLAY_NAME).orEmpty()
        val title = string(MediaStore.Audio.Media.TITLE)?.takeIf { it.isNotBlank() }
            ?: displayName.substringBeforeLast('.').ifBlank { "Unknown title" }
        val folder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            string(MediaStore.Audio.Media.RELATIVE_PATH).orEmpty().trimEnd('/')
        } else {
            @Suppress("DEPRECATION")
            val data = string(MediaStore.Audio.Media.DATA).orEmpty()
            File(data).parent.orEmpty()
                .removePrefix("/storage/emulated/0")
                .removePrefix("/sdcard")
                .trim('/')
        }
        val rawTrack = getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK))
        return Song(
            id = uri.toString(),
            title = title,
            artist = string(MediaStore.Audio.Media.ARTIST).cleanTag() ?: UNKNOWN_ARTIST,
            album = string(MediaStore.Audio.Media.ALBUM).cleanTag() ?: UNKNOWN_ALBUM,
            albumId = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)),
            durationMs = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)),
            // TRACK encodes disc * 1000 + track number.
            trackNumber = if (rawTrack >= 1000) rawTrack % 1000 else rawTrack,
            year = getInt(getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)),
            dateAddedSec = getLong(getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)),
            folder = folder,
            source = SongSource.MEDIA_STORE,
        )
    }

    private fun Cursor.string(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    companion object {
        private const val TAG = "MediaStoreScanner"
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
    }
}

/** MediaStore reports missing tags as "<unknown>". */
internal fun String?.cleanTag(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
