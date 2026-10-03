package com.example.cinelocal.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "iptv_channels")
data class IptvChannelEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val group: String = "Geral",
    val logo: String? = null,
    val country: String? = null,
    val url: String,
    val isFavorite: Boolean = false
)
