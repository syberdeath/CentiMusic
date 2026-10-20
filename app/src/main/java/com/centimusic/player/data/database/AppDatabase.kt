package com.centimusic.player.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        SearchHistoryEntity::class,
        PlayHistoryEntity::class,
        ImportedTrackEntity::class,
        SavedQueueEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun importedTrackDao(): ImportedTrackDao
    abstract fun queueDao(): QueueDao

    companion object {
        fun create(context: Context): AppDatabase =
            // File name predates the CentiMusic rename; kept so existing installs keep their playlists.
            Room.databaseBuilder(context, AppDatabase::class.java, "nothing_music.db").build()
    }
}
