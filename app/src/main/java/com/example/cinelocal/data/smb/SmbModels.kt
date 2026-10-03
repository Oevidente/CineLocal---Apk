package com.example.cinelocal.data.smb

import java.io.Serializable

data class DiscoveredPc(
    val ip: String,
    val hostName: String,
    val isReachable: Boolean = true
) : Serializable

data class SmbShareItem(
    val name: String,
    val comment: String? = null
)

data class SmbFileItem(
    val name: String,
    val isDirectory: Boolean,
    val path: String, // ex: "Filmes/Matrix/Matrix.mkv"
    val shareName: String,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val isVideo: Boolean = false
)

data class SmbConnectionConfig(
    val host: String,
    val port: Int = 445,
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val isAnonymous: Boolean = true
)
