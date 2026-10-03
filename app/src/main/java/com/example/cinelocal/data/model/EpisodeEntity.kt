package com.example.cinelocal.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mediaId"])]
)
data class EpisodeEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val mediaId: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val overview: String? = null,
    val stillPath: String? = null,
    val uriString: String? = null,
    val filePath: String? = null,
    val streamUrl: String? = null,
    val progressSeconds: Long = 0L,
    val durationSeconds: Long = 0L
)
