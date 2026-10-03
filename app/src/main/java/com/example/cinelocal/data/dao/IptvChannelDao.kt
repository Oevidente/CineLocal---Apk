package com.example.cinelocal.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cinelocal.data.model.IptvChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvChannelDao {

    @Query("SELECT * FROM iptv_channels ORDER BY name ASC")
    fun getAllChannels(): Flow<List<IptvChannelEntity>>

    @Query("SELECT * FROM iptv_channels WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteChannels(): Flow<List<IptvChannelEntity>>

    @Query("SELECT * FROM iptv_channels WHERE id = :id")
    suspend fun getChannelById(id: String): IptvChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: IptvChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<IptvChannelEntity>)

    @Query("UPDATE iptv_channels SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: String, isFavorite: Boolean)

    @Query("DELETE FROM iptv_channels WHERE id = :id")
    suspend fun deleteChannelById(id: String)

    @Query("DELETE FROM iptv_channels")
    suspend fun deleteAllChannels()
}
