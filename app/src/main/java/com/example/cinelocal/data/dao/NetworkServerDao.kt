package com.example.cinelocal.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.cinelocal.data.model.NetworkServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkServerDao {

    @Query("SELECT * FROM network_servers ORDER BY addedTimestamp DESC")
    fun getAllServers(): Flow<List<NetworkServerEntity>>

    @Query("SELECT * FROM network_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): NetworkServerEntity?

    @Query("SELECT * FROM network_servers WHERE host = :host LIMIT 1")
    suspend fun getServerByHost(host: String): NetworkServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: NetworkServerEntity)

    @Update
    suspend fun updateServer(server: NetworkServerEntity)

    @Delete
    suspend fun deleteServer(server: NetworkServerEntity)

    @Query("DELETE FROM network_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)
}
