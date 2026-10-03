package com.example.cinelocal.data.player

data class TrackInfo(
    val id: String,
    val name: String,
    val language: String? = null,
    val isSelected: Boolean = false,
    val groupIndex: Int = 0,
    val trackIndex: Int = 0
)
