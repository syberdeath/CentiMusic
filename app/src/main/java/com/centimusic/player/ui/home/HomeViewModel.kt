package com.centimusic.player.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.centimusic.player.AppContainer
import com.centimusic.player.data.media.Album
import com.centimusic.player.data.media.ScanState
import com.centimusic.player.data.media.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val scanState: ScanState = ScanState.Idle,
    val songCount: Int = 0,
    val allSongs: List<Song> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
    val recentlyAdded: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        container.music.library,
        container.music.scanState,
        container.history.recentlyPlayedIds,
    ) { library, scan, playedIds ->
        HomeUiState(
            loading = scan == ScanState.Idle || (scan == ScanState.Scanning && library.isEmpty),
            scanState = scan,
            songCount = library.songs.size,
            allSongs = library.songs,
            recentlyPlayed = playedIds.mapNotNull { library.byId[it] }.distinctBy { it.id }.take(20),
            recentlyAdded = library.songs.sortedByDescending { it.dateAddedSec }.take(20),
            albums = library.albums.sortedByDescending { album -> album.songs.maxOf { it.dateAddedSec } }.take(12),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun rescan() = viewModelScope.launch { container.music.rescan() }
}
