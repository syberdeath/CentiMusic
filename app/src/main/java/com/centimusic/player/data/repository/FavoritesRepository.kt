package com.centimusic.player.data.repository

import com.centimusic.player.data.database.FavoriteDao
import com.centimusic.player.data.database.FavoriteEntity
import com.centimusic.player.data.media.Library
import com.centimusic.player.data.media.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FavoritesRepository(private val dao: FavoriteDao, scope: CoroutineScope) {
    /** Liked media ids, most recently liked first. */
    val likedIds: StateFlow<List<String>> = dao.observeAll()
        .map { rows -> rows.map { it.mediaId } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun isLiked(mediaId: String): Boolean = mediaId in likedIds.value

    suspend fun setLiked(mediaId: String, liked: Boolean) {
        if (liked) dao.insert(FavoriteEntity(mediaId, System.currentTimeMillis())) else dao.delete(mediaId)
    }

    /** Liked songs as they appear in [library]: likes on a hidden duplicate count for the kept copy. */
    fun likedSongIds(likedIds: List<String>, library: Library): Set<String> =
        likedIds.mapNotNullTo(HashSet()) { library.byId[it]?.id }

    /** Like [song], or unlike it including any hidden duplicate copies that were liked. */
    suspend fun setLiked(song: Song, liked: Boolean, library: Library) {
        if (liked) {
            setLiked(song.id, true)
        } else {
            likedIds.value.filter { it == song.id || library.byId[it]?.id == song.id }.forEach { dao.delete(it) }
        }
    }

    suspend fun toggle(mediaId: String): Boolean {
        val liked = !dao.isFavorite(mediaId)
        setLiked(mediaId, liked)
        return liked
    }
}
