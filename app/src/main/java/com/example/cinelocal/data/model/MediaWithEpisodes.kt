package com.example.cinelocal.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class MediaWithEpisodes(
    @Embedded val media: MediaItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "mediaId"
    )
    val episodes: List<EpisodeEntity> = emptyList()
)
