package com.centimusic.player.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.centimusic.player.AppContainer
import com.centimusic.player.data.media.Album
import com.centimusic.player.data.media.Artist
import com.centimusic.player.data.media.Library
import com.centimusic.player.data.media.Song
import com.centimusic.player.data.repository.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer

data class SearchResults(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
) {
    val isEmpty get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}

data class SearchUiState(
    val results: SearchResults = SearchResults(),
    val recentSearches: List<String> = emptyList(),
    /** Artists with the most tracks in the user's own library (not trends). */
    val topArtists: List<String> = emptyList(),
    val librarySize: Int = 0,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val results = combine(_query.debounce(120), container.music.library, container.playlists.playlists) { q, library, playlists ->
        search(q.trim(), library, playlists)
    }.flowOn(Dispatchers.Default)

    val state: StateFlow<SearchUiState> = combine(results, container.history.recentSearches, container.music.library) { results, recent, library ->
        SearchUiState(
            results = results,
            recentSearches = recent,
            topArtists = library.artists
                .filter { it.name != "Unknown artist" }
                .sortedByDescending { it.songs.size }
                .take(8)
                .map { it.name },
            librarySize = library.songs.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) {
        _query.value = value
    }

    fun commit(value: String = _query.value) = viewModelScope.launch { container.history.recordSearch(value) }
    fun deleteRecent(value: String) = viewModelScope.launch { container.history.deleteSearch(value) }
    fun clearRecent() = viewModelScope.launch { container.history.clearSearches() }

    private fun search(q: String, library: Library, playlists: List<Playlist>): SearchResults {
        if (q.isEmpty()) return SearchResults()
        val needle = q.normalized()
        // Prefix matches first, then substring matches.
        fun rank(text: String): Int {
            val t = text.normalized()
            return when {
                t.startsWith(needle) -> 0
                t.split(' ').any { it.startsWith(needle) } -> 1
                needle in t -> 2
                else -> -1
            }
        }
        val songs = library.songs.mapNotNull { song ->
            val r = listOfNotNull(
                rank(song.title).takeIf { it >= 0 },
                rank(song.artist).takeIf { it >= 0 }?.plus(1),
                rank(song.album).takeIf { it >= 0 }?.plus(2),
            ).minOrNull()
            r?.let { it to song }
        }.sortedBy { it.first }.map { it.second }.take(60)
        val albums = library.albums.filter { rank(it.title) >= 0 || rank(it.artist) >= 0 }.take(20)
        val artists = library.artists.filter { rank(it.name) >= 0 }.take(20)
        val matchingPlaylists = playlists.filter { rank(it.name) >= 0 }
        return SearchResults(q, songs, albums, artists, matchingPlaylists)
    }
}

/** Case- and accent-insensitive form for matching ("Beyoncé" ≈ "beyonce"). */
internal fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

fun Song.matches(query: String): Boolean {
    val q = query.trim().normalized()
    return q in title.normalized() || q in artist.normalized() || q in album.normalized()
}
