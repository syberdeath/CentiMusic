package com.centimusic.player

import android.content.Context
import com.centimusic.player.data.database.AppDatabase
import com.centimusic.player.data.media.MusicRepository
import com.centimusic.player.data.preferences.PreferencesRepository
import com.centimusic.player.data.repository.FavoritesRepository
import com.centimusic.player.data.repository.HistoryRepository
import com.centimusic.player.data.repository.PlaylistRepository
import com.centimusic.player.playback.AudioOutputMonitor
import com.centimusic.player.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container; one instance per process, owned by [NothingMusicApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** Lives as long as the process; used for work that must outlive a screen. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val database: AppDatabase = AppDatabase.create(appContext)
    val preferences = PreferencesRepository(appContext)
    val music = MusicRepository(appContext, database.importedTrackDao(), preferences, scope)
    val favorites = FavoritesRepository(database.favoriteDao(), scope)
    val playlists = PlaylistRepository(database.playlistDao(), music.library)
    val history = HistoryRepository(database.historyDao())

    /** Created on first use from the UI; the playback service itself doesn't need a controller. */
    val player: PlayerConnection by lazy { PlayerConnection(appContext, scope, music.library) }
    val audioOutput: AudioOutputMonitor by lazy { AudioOutputMonitor(appContext) }
}
