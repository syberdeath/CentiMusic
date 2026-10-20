package com.centimusic.player.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.centimusic.player.AppContainer
import com.centimusic.player.data.media.Library
import com.centimusic.player.data.media.ScanState
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.media.SongSort
import com.centimusic.player.data.media.formatTotalDuration
import com.centimusic.player.data.media.pluralSongs
import com.centimusic.player.data.media.sortedFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SongListSource {
    val key: String

    data object AllSongs : SongListSource { override val key = "all" }
    data object Liked : SongListSource { override val key = "liked" }
    data object RecentlyAdded : SongListSource { override val key = "recent" }
    data object RecentlyPlayed : SongListSource { override val key = "history" }
    data class Album(val albumKey: String) : SongListSource { override val key = "album:$albumKey" }
    data class Artist(val name: String) : SongListSource { override val key = "artist:$name" }
    data class Folder(val path: String) : SongListSource { override val key = "folder:$path" }
}

enum class HeaderStyle { TITLE, ARTWORK, ARTIST }

data class SongListUiState(
    val loading: Boolean = true,
    val kind: String = "",
    val title: String = "",
    val subtitle: String = "",
    val songs: List<Song> = emptyList(),
    val cover: Song? = null,
    val headerStyle: HeaderStyle = HeaderStyle.TITLE,
    val sortable: Boolean = false,
    val sort: SongSort = SongSort.TITLE,
    val showTrackNumbers: Boolean = false,
    val likedIds: Set<String> = emptySet(),
    val emptyTitle: String = "Nothing here yet",
    val emptyMessage: String = "",
    /** All Songs only: duplicate filter state, or null where it doesn't apply. */
    val duplicates: DuplicateFilter? = null,
)

data class DuplicateFilter(val enabled: Boolean, val hidden: Int)

class SongListViewModel(private val container: AppContainer, private val source: SongListSource) : ViewModel() {
    val state: StateFlow<SongListUiState> = combine(
        container.music.library,
        container.music.scanState,
        container.favorites.likedIds,
        container.history.recentlyPlayedIds,
        container.preferences.preferences,
    ) { library, scan, liked, played, prefs ->
        build(library, scan, liked, played, prefs.songSort).let {
            if (source == SongListSource.AllSongs) it.copy(duplicates = DuplicateFilter(prefs.hideDuplicates, library.hiddenDuplicates)) else it
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SongListUiState())

    fun setSort(sort: SongSort) = viewModelScope.launch { container.preferences.setSongSort(sort) }
    fun setHideDuplicates(hide: Boolean) = viewModelScope.launch { container.preferences.setHideDuplicates(hide) }

    fun unlike(song: Song) = viewModelScope.launch { container.favorites.setLiked(song, false, container.music.library.value) }

    private fun build(library: Library, scan: ScanState, liked: List<String>, played: List<String>, sort: SongSort): SongListUiState {
        val loading = scan == ScanState.Idle || (scan == ScanState.Scanning && library.isEmpty)
        val likedSet = container.favorites.likedSongIds(liked, library)
        fun summary(songs: List<Song>) = "${songs.size.pluralSongs()} · ${songs.sumOf { it.durationMs }.formatTotalDuration()}"
        return when (source) {
            SongListSource.AllSongs -> {
                val songs = library.songs.sortedFor(sort)
                SongListUiState(loading, "Library", "All Songs", summary(songs), songs, sortable = true, sort = sort, likedIds = likedSet,
                    emptyMessage = "Songs on this device will appear here.")
            }
            SongListSource.Liked -> {
                val songs = liked.mapNotNull { library.byId[it] }.distinctBy { it.id }
                SongListUiState(loading, "Collection", "Liked Songs", summary(songs), songs, likedIds = likedSet,
                    emptyTitle = "No liked songs", emptyMessage = "Tap the heart on the player to save songs here.")
            }
            SongListSource.RecentlyAdded -> {
                val songs = library.songs.sortedByDescending { it.dateAddedSec }.take(100)
                SongListUiState(loading, "Library", "Recently Added", summary(songs), songs, likedIds = likedSet)
            }
            SongListSource.RecentlyPlayed -> {
                val songs = played.mapNotNull { library.byId[it] }.distinctBy { it.id }
                SongListUiState(loading, "History", "Recently Played", summary(songs), songs, likedIds = likedSet,
                    emptyTitle = "Nothing played yet", emptyMessage = "Songs you listen to will show up here.")
            }
            is SongListSource.Album -> {
                val album = library.album(source.albumKey)
                val songs = album?.songs.orEmpty()
                val year = album?.year?.takeIf { it > 0 }?.let { " · $it" }.orEmpty()
                SongListUiState(loading, "Album", album?.title ?: "Album", "${album?.artist.orEmpty()}$year\n${summary(songs)}", songs,
                    cover = album?.cover, headerStyle = HeaderStyle.ARTWORK, showTrackNumbers = true, likedIds = likedSet,
                    emptyTitle = "Album unavailable", emptyMessage = "These files are no longer on this device.")
            }
            is SongListSource.Artist -> {
                val artist = library.artist(source.name)
                val songs = artist?.songs.orEmpty()
                SongListUiState(loading, "Artist", source.name, "${plural(artist?.albumCount ?: 0, "album")} · ${summary(songs)}", songs,
                    cover = artist?.cover, headerStyle = HeaderStyle.ARTIST, likedIds = likedSet,
                    emptyTitle = "Artist unavailable", emptyMessage = "These files are no longer on this device.")
            }
            is SongListSource.Folder -> {
                val folder = library.folder(source.path)
                val songs = folder?.songs.orEmpty()
                SongListUiState(loading, "Folder", folder?.name ?: source.path, "${source.path.ifEmpty { "/" }}\n${summary(songs)}", songs,
                    cover = folder?.songs?.firstOrNull(), headerStyle = HeaderStyle.ARTWORK, likedIds = likedSet)
            }
        }
    }
}
