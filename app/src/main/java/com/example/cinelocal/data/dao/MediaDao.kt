package com.example.cinelocal.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.MediaWithEpisodes
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media_items ORDER BY addedTimestamp DESC")
    fun getAllMedia(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items")
    suspend fun getAllMediaList(): List<MediaItemEntity>

    @Transaction
    @Query("SELECT * FROM media_items ORDER BY addedTimestamp DESC")
    fun getAllMediaWithEpisodes(): Flow<List<MediaWithEpisodes>>

    @Transaction
    @Query("SELECT * FROM media_items WHERE id = :id")
    fun getMediaWithEpisodesById(id: String): Flow<MediaWithEpisodes?>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getMediaById(id: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItemEntity>)

    @Update
    suspend fun updateMedia(media: MediaItemEntity)

    @Query("UPDATE media_items SET progressSeconds = :progressSeconds, durationSeconds = :durationSeconds WHERE id = :id")
    suspend fun updateProgress(id: String, progressSeconds: Long, durationSeconds: Long)

    @Query("UPDATE media_items SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteMediaById(id: String)

    @Query("DELETE FROM media_items")
    suspend fun deleteAllMedia()
}
