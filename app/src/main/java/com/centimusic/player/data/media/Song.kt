package com.centimusic.player.data.media

import android.net.Uri
import androidx.compose.runtime.Immutable

/** Where a track came from: the device's MediaStore index, or a file the user picked via SAF. */
enum class SongSource { MEDIA_STORE, IMPORTED }

/**
 * A playable local track. [id] is the content URI string and doubles as the Media3 media id,
 * the favourites key and the playlist-entry key, so the same file is never listed twice.
 */
@Immutable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAddedSec: Long,
    val folder: String,
    val source: SongSource,
    /** Custom cover chosen in CentiMusic (app-private file), overriding the file's artwork. */
    val artworkPath: String? = null,
) {
    val uri: Uri get() = Uri.parse(id)

    /** Groups songs into albums. MediaStore album ids are reliable; imported files fall back to names. */
    val albumKey: String
        get() = if (source == SongSource.MEDIA_STORE && albumId > 0) "id:$albumId" else "name:${album.lowercase()}|${artist.lowercase()}"

    /** Songs on the same album share artwork, so cache artwork per album where possible. */
    val artworkKey: String
        get() = when {
            artworkPath != null -> "file:$artworkPath"
            source == SongSource.MEDIA_STORE && albumId > 0 -> "album:$albumId"
            else -> "song:$id"
        }
}

@Immutable
data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val cover: Song get() = songs.first()
    val durationMs: Long get() = songs.sumOf { it.durationMs }
}

@Immutable
data class Artist(
    val name: String,
    val songs: List<Song>,
    val albumCount: Int,
) {
    val cover: Song get() = songs.first()
}

@Immutable
data class Folder(
    val path: String,
    val songs: List<Song>,
) {
    val name: String get() = path.substringAfterLast('/').ifBlank { path.ifBlank { "Internal storage" } }
}

/** Snapshot of the whole local library with derived groupings computed once per scan. */
@Immutable
data class Library(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val byId: Map<String, Song> = emptyMap(),
    /** Duplicate copies hidden from the library; their ids still resolve (to the kept copy) via [byId]. */
    val hiddenDuplicates: Int = 0,
) {
    val isEmpty: Boolean get() = songs.isEmpty()

    fun album(key: String): Album? = albums.firstOrNull { it.key == key }
    fun artist(name: String): Artist? = artists.firstOrNull { it.name == name }
    fun folder(path: String): Folder? = folders.firstOrNull { it.path == path }

    companion object {
        fun from(songs: List<Song>, hideDuplicates: Boolean = false): Library {
            val all = songs.distinctBy { it.id }
            val aliases = if (hideDuplicates) Duplicates.find(all) else emptyMap()
            val distinct = if (aliases.isEmpty()) all else all.filterNot { it.id in aliases }
            val albumOrder = compareBy<Song>({ it.trackNumber.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.title.lowercase() })
            val albums = distinct.groupBy { it.albumKey }.map { (key, list) ->
                val sorted = list.sortedWith(albumOrder)
                Album(
                    key = key,
                    title = sorted.first().album,
                    artist = sorted.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: sorted.first().artist,
                    year = sorted.maxOf { it.year },
                    songs = sorted,
                )
            }.sortedBy { it.title.lowercase() }
            val artists = distinct.groupBy { it.artist }.map { (name, list) ->
                Artist(
                    name = name,
                    songs = list.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() })),
                    albumCount = list.distinctBy { it.albumKey }.size,
                )
            }.sortedBy { it.name.lowercase() }
            val folders = distinct.groupBy { it.folder }.map { (path, list) ->
                Folder(path, list.sortedBy { it.title.lowercase() })
            }.sortedBy { it.path.lowercase() }
            val byId = HashMap<String, Song>(all.size)
            distinct.associateByTo(byId) { it.id }
            // Playlists, likes and saved queues that point at a hidden copy keep working.
            aliases.forEach { (hidden, kept) -> byId[kept]?.let { byId[hidden] = it } }
            return Library(distinct, albums, artists, folders, byId, aliases.size)
        }
    }
}

enum class SongSort(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DATE_ADDED("Recently added"),
    DURATION("Duration");

    fun comparator(): Comparator<Song> = when (this) {
        TITLE -> compareBy { it.title.lowercase() }
        ARTIST -> compareBy<Song>({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber })
        ALBUM -> compareBy<Song>({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() })
        DATE_ADDED -> compareByDescending { it.dateAddedSec }
        DURATION -> compareByDescending { it.durationMs }
    }
}

fun List<Song>.sortedFor(sort: SongSort): List<Song> = sortedWith(sort.comparator())

fun Long.formatDuration(): String {
    val totalSec = (this / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** "1 hr 12 min" style used for playlist and album totals. */
fun Long.formatTotalDuration(): String {
    val totalMin = this / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "$h hr $m min"
        h > 0 -> "$h hr"
        else -> "$m min"
    }
}

fun Int.pluralSongs(): String = if (this == 1) "1 song" else "$this songs"
