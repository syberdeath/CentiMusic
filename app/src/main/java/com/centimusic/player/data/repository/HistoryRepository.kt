package com.centimusic.player.data.repository

import com.centimusic.player.data.database.HistoryDao
import com.centimusic.player.data.database.PlayHistoryEntity
import com.centimusic.player.data.database.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(private val dao: HistoryDao) {
    val recentSearches: Flow<List<String>> = dao.observeSearches().map { rows -> rows.map { it.query } }
    val recentlyPlayedIds: Flow<List<String>> = dao.observeRecentlyPlayed().map { rows -> rows.map { it.mediaId } }

    suspend fun recordSearch(query: String) {
        val q = query.trim()
        if (q.length >= 2) dao.upsertSearch(SearchHistoryEntity(q, System.currentTimeMillis()))
    }

    suspend fun deleteSearch(query: String) = dao.deleteSearch(query)
    suspend fun clearSearches() = dao.clearSearches()

    suspend fun recordPlay(mediaId: String) {
        val count = dao.playEntry(mediaId)?.playCount ?: 0
        dao.upsertPlay(PlayHistoryEntity(mediaId, System.currentTimeMillis(), count + 1))
    }
}
