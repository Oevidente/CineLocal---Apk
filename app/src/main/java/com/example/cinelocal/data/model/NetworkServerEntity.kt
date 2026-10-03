package com.example.cinelocal.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "network_servers")
data class NetworkServerEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 445,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val isAnonymous: Boolean = true,
    val lastShare: String? = null,
    val lastPath: String? = null,
    val addedTimestamp: Long = System.currentTimeMillis()
)
