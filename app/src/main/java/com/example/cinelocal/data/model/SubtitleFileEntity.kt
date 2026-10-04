package com.example.cinelocal.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "subtitle_files",
    foreignKeys = [
        ForeignKey(
            entity = EpisodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["episodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("episodeId")]
)
data class SubtitleFileEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val episodeId: String,
    val language: String,        // e.g. "pt-BR", "pt", "en"
    val label: String,           // Release name or subtitle description
    val filePath: String,        // Local storage file path in filesDir/subtitles/...
    val source: String = "opensubtitles", // "opensubtitles" | "embedded-sidecar" | "manual"
    val osFileId: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)
