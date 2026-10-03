package com.example.cinelocal.data.torrent

import android.content.Context
import android.util.Log
import com.github.se_bastiaan.torrentstream.Torrent
import com.github.se_bastiaan.torrentstream.TorrentOptions
import com.github.se_bastiaan.torrentstream.StreamStatus
import com.github.se_bastiaan.torrentstream.TorrentStream
import com.github.se_bastiaan.torrentstream.listeners.TorrentListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class TorrentEngineStatus(
    val isStreaming: Boolean = false,
    val infoHash: String = "",
    val displayName: String = "",
    val peersCount: Int = 0,
    val downloadSpeedBps: Long = 0L,
    val bufferedPercent: Int = 0,
    val downloadPercent: Float = 0f,
    val totalSizeBytes: Long = 0L,
    val stage: String = "Inativo",
    val error: String? = null,
    val currentVideoFile: File? = null
)

object TorrentStreamEngine : TorrentListener {

    private const val TAG = "TorrentStreamEngine"

    private var torrentStream: TorrentStream? = null
    private val _status = MutableStateFlow(TorrentEngineStatus())
    val status: StateFlow<TorrentEngineStatus> = _status.asStateFlow()

    @Volatile
    var isInitialized = false
        private set

    @Synchronized
    fun init(context: Context) {
        if (isInitialized && torrentStream != null) return
        try {
            val torrentDir = File(context.cacheDir, "torrents")
            if (!torrentDir.exists()) {
                torrentDir.mkdirs()
            }

            val torrentOptions = TorrentOptions.Builder()
                .saveLocation(torrentDir)
                .removeFilesAfterStop(true)
                .build()

            torrentStream = TorrentStream.init(torrentOptions)
            torrentStream?.addListener(this)
            isInitialized = true
            Log.d(TAG, "TorrentStreamEngine inicializado em ${torrentDir.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar TorrentStreamEngine", e)
            _status.value = TorrentEngineStatus(error = "Falha ao iniciar motor de torrent: ${e.localizedMessage}")
        }
    }

    fun startStream(context: Context, magnetUri: String) {
        init(context)
        val parsed = TorrentUtils.parseMagnetUri(magnetUri)
        val hash = parsed?.infoHash ?: TorrentUtils.extractInfoHash(magnetUri) ?: ""
        val name = parsed?.displayName ?: "Torrent"

        _status.value = TorrentEngineStatus(
            isStreaming = true,
            infoHash = hash,
            displayName = name,
            stage = "Buscando metadados do enxame..."
        )

        try {
            torrentStream?.startStream(magnetUri)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar stream do magnet", e)
            _status.value = _status.value.copy(
                isStreaming = false,
                stage = "Erro ao conectar",
                error = e.localizedMessage ?: "Erro ao iniciar stream"
            )
        }
    }

    fun stop() {
        try {
            torrentStream?.stopStream()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar stream", e)
        }
        _status.value = TorrentEngineStatus(stage = "Inativo")
    }

    override fun onStreamPrepared(torrent: Torrent) {
        val videoFile = torrent.videoFile
        val size = videoFile?.length() ?: 0L
        Log.d(TAG, "Torrent preparado: ${videoFile?.name}, tamanho real: $size")
        _status.value = _status.value.copy(
            stage = "Metadados recebidos. Aguardando buffer inicial...",
            currentVideoFile = videoFile,
            totalSizeBytes = size
        )
    }

    override fun onStreamStarted(torrent: Torrent) {
        Log.d(TAG, "Torrent iniciado no enxame")
        _status.value = _status.value.copy(
            stage = "Conectado ao enxame. Baixando peças..."
        )
    }

    override fun onStreamError(torrent: Torrent?, e: Exception?) {
        val errorMsg = e?.localizedMessage ?: "Erro desconhecido no torrent"
        Log.e(TAG, "Erro no stream do torrent: $errorMsg", e)
        _status.value = _status.value.copy(
            isStreaming = false,
            stage = "Erro",
            error = errorMsg
        )
    }

    override fun onStreamReady(torrent: Torrent) {
        val videoFile = torrent.videoFile
        Log.d(TAG, "Stream pronto para reprodução: ${videoFile?.absolutePath}")
        _status.value = _status.value.copy(
            stage = "Buffer inicial concluído. Reproduzindo",
            currentVideoFile = videoFile,
            bufferedPercent = 100
        )
    }

    override fun onStreamProgress(torrent: Torrent, torrentStatus: StreamStatus) {
        val seeds = torrentStatus.seeds
        val speed = torrentStatus.downloadSpeed.toLong()
        val progress = torrentStatus.progress
        val buffer = torrentStatus.bufferProgress

        Log.d(TAG, "Progresso P2P real: peers=$seeds speed=$speed B/s buffer=$buffer% progress=$progress%")

        _status.value = _status.value.copy(
            peersCount = seeds,
            downloadSpeedBps = speed,
            bufferedPercent = buffer,
            downloadPercent = progress,
            stage = if (buffer < 100) "Preparando buffer: $buffer%" else "Transmissão P2P ativa"
        )
    }

    override fun onStreamStopped() {
        Log.d(TAG, "Stream de torrent encerrado")
        _status.value = TorrentEngineStatus(stage = "Inativo")
    }
}
