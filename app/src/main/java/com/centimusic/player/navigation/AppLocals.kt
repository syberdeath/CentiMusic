package com.centimusic.player.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.centimusic.player.AppContainer
import com.centimusic.player.data.media.Song

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }

/** Height of the bottom navigation + mini-player, so scrolling content can clear it. */
val LocalChromePadding = compositionLocalOf { 0.dp }

/** Shows a short message at the bottom of the screen. */
val LocalMessenger = staticCompositionLocalOf<(String) -> Unit> { {} }

data class SongMenuRequest(val song: Song, val playlistId: Long? = null)

@Stable
class SongMenuController {
    var request by mutableStateOf<SongMenuRequest?>(null)
        private set
    var addToPlaylist by mutableStateOf<List<Song>?>(null)
        private set

    fun open(song: Song, playlistId: Long? = null) {
        request = SongMenuRequest(song, playlistId)
    }

    fun dismiss() {
        request = null
    }

    fun pickPlaylistFor(songs: List<Song>) {
        request = null
        addToPlaylist = songs
    }

    fun dismissPlaylistPicker() {
        addToPlaylist = null
    }
}

val LocalSongMenu = staticCompositionLocalOf { SongMenuController() }

/** Audio-permission state and the actions that resolve it. */
data class LibraryAccess(
    val granted: Boolean,
    val permanentlyDenied: Boolean,
    val request: () -> Unit,
    val importFiles: () -> Unit,
)

/** Replays the launch animation (Settings → Preview splash). */
val LocalReplaySplash = staticCompositionLocalOf<() -> Unit> { {} }

val LocalLibraryAccess = compositionLocalOf { LibraryAccess(true, false, {}, {}) }
