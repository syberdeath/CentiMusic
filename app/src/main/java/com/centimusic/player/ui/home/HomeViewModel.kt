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
    val mostPlayed: List<Song> = emptyList(),
    val liked: List<Song> = emptyList(),
    val likedCount: Int = 0,
    val albums: List<Album> = emptyList(),
)

data class WeekStats(val plays: List<Int>, val dayLabels: List<String>, val minutes: Int)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        container.music.library,
        container.music.scanState,
        container.history.recentlyPlayedIds,
        container.history.mostPlayedIds,
        container.favorites.likedIds,
    ) { library, scan, playedIds, mostIds, likedIds ->
        HomeUiState(
            loading = scan == ScanState.Idle || (scan == ScanState.Scanning && library.isEmpty),
            scanState = scan,
            songCount = library.songs.size,
            allSongs = library.songs,
            recentlyPlayed = playedIds.mapNotNull { library.byId[it] }.distinctBy { it.id }.take(20),
            recentlyAdded = library.songs.sortedByDescending { it.dateAddedSec }.take(20),
            mostPlayed = mostIds.mapNotNull { library.byId[it] }.distinctBy { it.id }.take(20),
            liked = likedIds.mapNotNull { library.byId[it] }.distinctBy { it.id }.take(20),
            likedCount = likedIds.mapNotNullTo(HashSet()) { library.byId[it]?.id }.size,
            albums = library.albums.sortedByDescending { album -> album.songs.maxOf { it.dateAddedSec } }.take(12),
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Plays per day for the last seven days (oldest first) and the minutes they add up to. */
    val week: StateFlow<WeekStats> = run {
        val days = (0..6).map { back ->
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                add(java.util.Calendar.DAY_OF_YEAR, back - 6)
            }
        }
        val starts = days.map { it.timeInMillis }
        val labels = days.map { it.getDisplayName(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SHORT, java.util.Locale.getDefault()).orEmpty().take(1).uppercase() }
        combine(container.history.listensSince(starts.first()), container.music.library) { listens, library ->
            val counts = IntArray(7)
            var ms = 0L
            listens.forEach { l ->
                val day = starts.indexOfLast { l.playedAt >= it }
                if (day >= 0) counts[day]++
                ms += library.byId[l.mediaId]?.durationMs ?: 0L
            }
            WeekStats(counts.toList(), labels, (ms / 60_000).toInt())
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekStats(List(7) { 0 }, labels, 0))
    }

    fun rescan() = viewModelScope.launch { container.music.rescan() }
}
