package com.example.cinelocal.player

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
 * Provedor padrão desativado até implementação do motor real de torrent.
 */
object DisabledTorrentStreamProvider : TorrentStreamProvider {
    override suspend fun prepare(magnetUri: String, onProgress: (TorrentProgress) -> Unit): PreparedTorrent {
        throw UnsupportedOperationException("O motor de torrent ainda não está ativo.")
    }

    override fun streamUrl(file: TorrentVideoFile): String {
        throw UnsupportedOperationException("O motor de torrent ainda não está ativo.")
    }

    override suspend fun stop() {}
}
