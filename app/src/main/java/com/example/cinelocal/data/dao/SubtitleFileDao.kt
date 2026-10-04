package com.example.cinelocal.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cinelocal.data.model.SubtitleFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleFileDao {
    @Query("SELECT * FROM subtitle_files WHERE episodeId = :episodeId ORDER BY addedAt DESC")
    fun getSubtitlesForEpisode(episodeId: String): Flow<List<SubtitleFileEntity>>

    @Query("SELECT * FROM subtitle_files WHERE episodeId = :episodeId ORDER BY addedAt DESC")
    suspend fun getSubtitlesListForEpisode(episodeId: String): List<SubtitleFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitle(subtitle: SubtitleFileEntity)

    @Query("DELETE FROM subtitle_files WHERE id = :id")
    suspend fun deleteSubtitleById(id: String)

    @Query("DELETE FROM subtitle_files WHERE episodeId = :episodeId")
    suspend fun deleteSubtitlesForEpisode(episodeId: String)

    @Query("DELETE FROM subtitle_files WHERE episodeId NOT IN (SELECT id FROM episodes)")
    suspend fun deleteOrphanSubtitles(): Int

    @Query("SELECT filePath FROM subtitle_files WHERE episodeId NOT IN (SELECT id FROM episodes)")
    suspend fun getOrphanSubtitlePaths(): List<String>
}
