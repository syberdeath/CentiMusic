package com.centimusic.player.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        FavoriteEntity::class,
        SearchHistoryEntity::class,
        PlayHistoryEntity::class,
        ImportedTrackEntity::class,
        SavedQueueEntity::class,
        SongEditEntity::class,
        ListenEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun historyDao(): HistoryDao
    abstract fun importedTrackDao(): ImportedTrackDao
    abstract fun queueDao(): QueueDao
    abstract fun songEditDao(): SongEditDao

    companion object {
        fun create(context: Context): AppDatabase =
            // File name predates the CentiMusic rename; kept so existing installs keep their playlists.
            Room.databaseBuilder(context, AppDatabase::class.java, "nothing_music.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v3 adds listens, a per-play log for the weekly chart. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `listens` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`mediaId` TEXT NOT NULL, `playedAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_listens_playedAt` ON `listens` (`playedAt`)")
            }
        }

        /** v2 adds song_edits; everything else (playlists, likes, history) is kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `song_edits` (`mediaId` TEXT NOT NULL, `title` TEXT, `artist` TEXT, " +
                        "`album` TEXT, `artworkPath` TEXT, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`mediaId`))",
                )
            }
        }
    }
}
