package com.example.cinelocal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cinelocal.data.dao.EpisodeDao
import com.example.cinelocal.data.dao.IptvChannelDao
import com.example.cinelocal.data.dao.MediaDao
import com.example.cinelocal.data.dao.NetworkServerDao
import com.example.cinelocal.data.dao.SettingDao
import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.IptvChannelEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.NetworkServerEntity
import com.example.cinelocal.data.model.SettingEntity

@Database(
    entities = [
        MediaItemEntity::class,
        EpisodeEntity::class,
        IptvChannelEntity::class,
        SettingEntity::class,
        NetworkServerEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mediaDao(): MediaDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun iptvChannelDao(): IptvChannelDao
    abstract fun settingDao(): SettingDao
    abstract fun networkServerDao(): NetworkServerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT INTO episodes (id, mediaId, seasonNumber, episodeNumber, title,
                                          uriString, filePath, streamUrl, progressSeconds, durationSeconds)
                    SELECT lower(hex(randomblob(16))), m.id, 0, 1, m.title,
                           m.uriString, m.filePath, m.streamUrl, m.progressSeconds, m.durationSeconds
                    FROM media_items m
                    WHERE m.kind IN ('MOVIE','TORRENT')
                      AND NOT EXISTS (SELECT 1 FROM episodes e WHERE e.mediaId = m.id)
                      AND NOT (m.uriString LIKE '%/tree/%' AND m.uriString NOT LIKE '%/document/%')
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `network_servers` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `host` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `username` TEXT NOT NULL,
                        `password` TEXT NOT NULL,
                        `domain` TEXT NOT NULL,
                        `isAnonymous` INTEGER NOT NULL,
                        `lastShare` TEXT,
                        `lastPath` TEXT,
                        `addedTimestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cinelocal.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

