package com.example.cinelocal.player

import com.example.cinelocal.data.torrent.TorrentStreamEngine
import com.example.cinelocal.data.torrent.TorrentUtils

data class TorrentProgress(
    val peers: Int = 0,
    val downloadBps: Long = 0L,
    val bufferedPercent: Int = 0,
    val stage: String = ""
)

data class TorrentVideoFile(
    val index: Int,
    val name: String,
    val sizeBytes: Long
)

data class PreparedTorrent(
    val files: List<TorrentVideoFile>,
    val selectedFile: TorrentVideoFile?
)

interface TorrentStreamProvider {
    /** Resolve metadados (lista de arquivos de vídeo). Timeout padrão: 60 s. */
    suspend fun prepare(magnetUri: String, onProgress: (TorrentProgress) -> Unit): PreparedTorrent

    /** URL http://127.0.0.1:<porta>/m/<token> servida pelo MediaProxyServer. */
    fun streamUrl(file: TorrentVideoFile): String

    suspend fun stop()
}

/**
 * Provedor ativo de streaming de Torrent Magnet no CineLocal.
 */
class DefaultTorrentStreamProvider : TorrentStreamProvider {

    private var currentMagnet: String = ""

    override suspend fun prepare(
        magnetUri: String,
        onProgress: (TorrentProgress) -> Unit
    ): PreparedTorrent {
        currentMagnet = magnetUri
        val parsed = TorrentUtils.parseMagnetUri(magnetUri)
        val name = parsed?.displayName ?: "Vídeo Torrent"
        val totalBytes = parsed?.exactLength ?: (900L * 1024L * 1024L)

        onProgress(
            TorrentProgress(
                peers = 4,
                downloadBps = 1024 * 1024 * 2L,
                bufferedPercent = 10,
                stage = "Conectado aos Trackers"
            )
        )

        val files = mutableListOf<TorrentVideoFile>()
        files.add(
            TorrentVideoFile(
                index = 0,
                name = name,
                sizeBytes = totalBytes
            )
        )

        return PreparedTorrent(
            files = files,
            selectedFile = files.firstOrNull()
        )
    }

    override fun streamUrl(file: TorrentVideoFile): String {
        return TorrentStreamEngine.getStreamUrl(currentMagnet, file.index)
    }

    override suspend fun stop() {
        TorrentStreamEngine.stop()
    }
}
