package com.example.cinelocal.data.model

data class TrackInfo(
    val id: String = "",
    val name: String = "",
    val language: String? = null,
    val isSelected: Boolean = false,
    val groupIndex: Int = 0,
    val trackIndex: Int = 0,
    val index: Int = trackIndex,
    val label: String = name
)
