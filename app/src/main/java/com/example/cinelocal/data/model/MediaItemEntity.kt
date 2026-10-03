package com.example.cinelocal.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val originalTitle: String? = null,
    val genres: String? = null,
    val kind: MediaKind,
    val overview: String? = null,
    val tagline: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val year: Int? = null,
    val rating: Float? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null,
    val isFavorite: Boolean = false,
    val uriString: String? = null,
    val filePath: String? = null,
    val streamUrl: String? = null,
    val infoHash: String? = null,
    val progressSeconds: Long = 0L,
    val durationSeconds: Long = 0L,
    val addedTimestamp: Long = System.currentTimeMillis()
)
