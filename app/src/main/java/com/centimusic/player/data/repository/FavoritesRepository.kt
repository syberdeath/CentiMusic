package com.centimusic.player.data.repository

import com.centimusic.player.data.database.FavoriteDao
import com.centimusic.player.data.database.FavoriteEntity
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

    suspend fun toggle(mediaId: String): Boolean {
        val liked = !dao.isFavorite(mediaId)
        setLiked(mediaId, liked)
        return liked
    }
}
