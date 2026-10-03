package com.example.cinelocal.player

import android.content.Context
import com.example.cinelocal.data.torrent.TorrentStreamEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

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
    suspend fun prepare(context: Context, magnetUri: String, onProgress: (TorrentProgress) -> Unit): PreparedTorrent

    /** Retorna a URL de vídeo ou caminho de arquivo baixado pelo motor de torrent. */
    fun streamUrl(file: TorrentVideoFile): String

    suspend fun stop()
}

/**
 * Provedor ativo de streaming de Torrent Magnet real no CineLocal.
 */
class DefaultTorrentStreamProvider : TorrentStreamProvider {

    private var currentMagnet: String = ""

    override suspend fun prepare(
        context: Context,
        magnetUri: String,
        onProgress: (TorrentProgress) -> Unit
    ): PreparedTorrent {
        currentMagnet = magnetUri
        
        TorrentStreamEngine.startStream(context, magnetUri)

        val result = withTimeoutOrNull(60_000L) {
            while (true) {
                val st = TorrentStreamEngine.status.value
                onProgress(
                    TorrentProgress(
                        peers = st.peersCount,
                        downloadBps = st.downloadSpeedBps,
                        bufferedPercent = st.bufferedPercent,
                        stage = st.stage
                    )
                )

                if (st.currentVideoFile != null) {
                    val file = st.currentVideoFile
                    val videoFile = TorrentVideoFile(
                        index = 0,
                        name = file.name,
                        sizeBytes = file.length()
                    )
                    return@withTimeoutOrNull PreparedTorrent(
                        files = listOf(videoFile),
                        selectedFile = videoFile
                    )
                }

                if (st.error != null) {
                    throw Exception(st.error)
                }

                delay(500)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }

        if (result == null) {
            TorrentStreamEngine.stop()
            throw IllegalStateException("Sem fontes (peers) ou tempo esgotado para resolver o torrent.")
        }

        return result
    }

    override fun streamUrl(file: TorrentVideoFile): String {
        val st = TorrentStreamEngine.status.value
        return st.currentVideoFile?.absolutePath ?: ""
    }

    override suspend fun stop() {
        TorrentStreamEngine.stop()
    }
}
