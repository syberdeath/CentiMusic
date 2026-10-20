package com.centimusic.player.navigation

import android.net.Uri
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SONGS = "songs"
    const val LIKED = "liked"
    const val RECENT = "recent"
    const val HISTORY = "history"
    const val ALBUMS = "albums"
    const val ALBUM = "album/{key}"
    const val ARTISTS = "artists"
    const val ARTIST = "artist/{name}"
    const val FOLDERS = "folders"
    const val FOLDER = "folder/{path}"
    const val PLAYLISTS = "playlists"
    const val PLAYLIST = "playlist/{id}"
    const val PLAYLIST_ADD = "playlist/{id}/add"
    const val SETTINGS = "settings"
    const val PLAYER = "player"
    const val QUEUE = "queue"

    val tabs = listOf(HOME, SEARCH, LIBRARY)

    /** Full-screen destinations that hide the bottom navigation and mini-player. */
    val immersive = setOf(PLAYER, QUEUE)
}

/** Typed navigation actions so screens never build route strings themselves. */
class Navigator(private val nav: NavHostController) {
    fun back() {
        nav.popBackStack()
    }

    fun tab(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun songs() = nav.navigate(Routes.SONGS)
    fun liked() = nav.navigate(Routes.LIKED)
    fun recentlyAdded() = nav.navigate(Routes.RECENT)
    fun recentlyPlayed() = nav.navigate(Routes.HISTORY)
    fun albums() = nav.navigate(Routes.ALBUMS)
    fun album(key: String) = nav.navigate("album/${Uri.encode(key)}")
    fun artists() = nav.navigate(Routes.ARTISTS)
    fun artist(name: String) = nav.navigate("artist/${Uri.encode(name)}")
    fun folders() = nav.navigate(Routes.FOLDERS)
    fun folder(path: String) = nav.navigate("folder/${Uri.encode(path.ifEmpty { "/" })}")
    fun playlists() = nav.navigate(Routes.PLAYLISTS)
    fun playlist(id: Long) = nav.navigate("playlist/$id")
    fun addToPlaylist(id: Long) = nav.navigate("playlist/$id/add")
    fun settings() = nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
    fun player() = nav.navigate(Routes.PLAYER) { launchSingleTop = true }
    fun queue() = nav.navigate(Routes.QUEUE) { launchSingleTop = true }
}
