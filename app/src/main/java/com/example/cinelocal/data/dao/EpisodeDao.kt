package com.example.cinelocal.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cinelocal.data.model.EpisodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {

    @Query("SELECT * FROM episodes WHERE mediaId = :mediaId ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getEpisodesForMedia(mediaId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: String): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET progressSeconds = :progressSeconds, durationSeconds = :durationSeconds WHERE id = :id")
    suspend fun updateProgress(id: String, progressSeconds: Long, durationSeconds: Long)

    @Query("DELETE FROM episodes WHERE mediaId = :mediaId")
    suspend fun deleteEpisodesForMedia(mediaId: String)
}
