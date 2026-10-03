package com.example.cinelocal.player

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.cinelocal.cast.CastManager
import com.example.cinelocal.cast.CastState
import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.IptvChannelEntity
import com.example.cinelocal.data.model.TrackInfo
import com.example.cinelocal.data.repository.MediaRepository
import com.example.cinelocal.data.torrent.TorrentUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

enum class ResizeMode(val mode: Int, val label: String) {
    FIT(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Ajustar"),
    ZOOM(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Preencher (Zoom)"),
    FILL(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Esticar"),
    FIXED_WIDTH(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH, "Largura"),
    FIXED_HEIGHT(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT, "Altura")
}

data class PlayerUiState(
    val title: String = "",
    val subtitle: String = "",
    val isLive: Boolean = false,
    val isTorrent: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = true,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val bufferedPosition: Long = 0,
    val playbackSpeed: Float = 1.0f,
    val resizeMode: ResizeMode = ResizeMode.FIT,
    val availableAudioTracks: List<TrackInfo> = emptyList(),
    val availableSubtitleTracks: List<TrackInfo> = emptyList(),
    val selectedAudioIndex: Int = -1,
    val selectedSubtitleIndex: Int = -1,
    val activeExternalSubtitleLabel: String? = null,
    val errorMessage: String? = null,
    val errorDetails: String? = null,
    val canRetry: Boolean = false,
    val hasNextEpisode: Boolean = false,
    val nextEpisodeTitle: String? = null,
    val isCasting: Boolean = false,
    val castDeviceName: String? = null
)

@OptIn(UnstableApi::class)
class PlayerViewModel(
    application: Application,
    private val repository: MediaRepository
) : AndroidViewModel(application) {

    val castManager: CastManager = CastManager.getInstance(application)
    val castState: StateFlow<CastState> = castManager.castState

    private var exoPlayer: ExoPlayer? = null
    val player: ExoPlayer
        get() = exoPlayer ?: createPlayer().also { exoPlayer = it }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _openSubtitlesApiKey = MutableStateFlow("")
    val openSubtitlesApiKey: StateFlow<String> = _openSubtitlesApiKey.asStateFlow()

    private var progressTrackingJob: Job? = null
    private var currentEpisode: EpisodeEntity? = null
    private var currentChannel: IptvChannelEntity? = null
    private var currentMediaTitle: String = ""
    private var allEpisodesInSeries: List<EpisodeEntity> = emptyList()
    private var currentSourceUri: Uri? = null
    private var currentVttFile: File? = null
    private var currentVttContent: String? = null

    init {
        castManager.init()
        viewModelScope.launch {
            val key = repository.getSetting("opensubtitles_api_key").firstOrNull() ?: ""
            _openSubtitlesApiKey.value = key
        }
        viewModelScope.launch {
            castState.collect { cState ->
                _uiState.value = _uiState.value.copy(
                    isCasting = cState.isConnected,
                    castDeviceName = cState.deviceName
                )
            }
        }
    }

    private fun createPlayer(): ExoPlayer {
        val context = getApplication<Application>()

        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("CineLocal/${com.example.cinelocal.BuildConfig.VERSION_NAME}")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val isBuffering = playbackState == Player.STATE_BUFFERING
                        val isEnded = playbackState == Player.STATE_ENDED
                        _uiState.value = _uiState.value.copy(
                            isBuffering = isBuffering,
                            duration = if (duration > 0 && duration != C.TIME_UNSET) duration else 0
                        )

                        if (isEnded && _uiState.value.hasNextEpisode) {
                            playNextEpisode()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        updateAvailableTracks(tracks)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val userMessage = when (error.errorCode) {
                            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                                "Arquivo não encontrado. Ele foi movido ou o pendrive/PC está desconectado?"
                            PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                                "Sem permissão para ler o arquivo. Adicione a pasta de novo."
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                                val responseCode = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
                                if (responseCode != null) "O servidor respondeu com erro HTTP $responseCode"
                                else "O servidor respondeu com erro HTTP inesperado."
                            }
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                                "Sem conexão com o servidor."
                            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
                                "Link HTTP bloqueado pelo sistema de segurança do aparelho."
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ->
                                "Arquivo inválido ou formato não suportado."
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                                "Este aparelho não decodifica o vídeo/áudio deste arquivo (codec não suportado)."
                            else -> error.localizedMessage ?: "Erro desconhecido na reprodução"
                        }

                        val technicalDetails = buildString {
                            appendLine("Código: ${error.errorCodeName} (${error.errorCode})")
                            currentSourceUri?.let { uri ->
                                appendLine("Fonte: ${sanitizeUrl(uri.toString())}")
                            }
                            error.cause?.let { cause ->
                                appendLine("Causa: ${cause.javaClass.simpleName}: ${cause.message}")
                            }
                            appendLine("Mensagem: ${error.message}")
                        }

                        android.util.Log.e("Player", "${error.errorCodeName} src=$currentSourceUri", error)

                        _uiState.value = _uiState.value.copy(
                            errorMessage = userMessage,
                            errorDetails = technicalDetails,
                            canRetry = true,
                            isBuffering = false
                        )
                    }
                })
            }
    }

    fun playMediaEpisode(
        episode: EpisodeEntity,
        mediaTitle: String,
        allEpisodes: List<EpisodeEntity> = emptyList(),
        startPositionMs: Long? = null
    ) {
        currentEpisode = episode
        currentChannel = null
        currentMediaTitle = mediaTitle
        allEpisodesInSeries = allEpisodes

        val currentIndex = allEpisodes.indexOfFirst { it.id == episode.id }
        val nextEp = if (currentIndex in 0 until (allEpisodes.size - 1)) allEpisodes[currentIndex + 1] else null

        val isMagnet = (episode.streamUrl?.startsWith("magnet:", ignoreCase = true) == true) ||
            (episode.uriString?.startsWith("magnet:", ignoreCase = true) == true) ||
            (episode.filePath?.startsWith("magnet:", ignoreCase = true) == true)
        val parsedMagnet = if (isMagnet) TorrentUtils.parseMagnet(episode.streamUrl ?: episode.uriString ?: episode.filePath ?: "") else null

        _uiState.value = PlayerUiState(
            title = mediaTitle,
            subtitle = if (isMagnet && parsedMagnet != null) "Torrent P2P • ${parsedMagnet.infoHash.take(8)}" else episode.title,
            isLive = false,
            isTorrent = isMagnet,
            isBuffering = true,
            hasNextEpisode = nextEp != null,
            nextEpisodeTitle = nextEp?.title,
            isCasting = castState.value.isConnected,
            castDeviceName = castState.value.deviceName
        )

        val resumePos = startPositionMs ?: (episode.progressSeconds * 1000)

        viewModelScope.launch {
            val media = repository.getMediaById(episode.mediaId)
            val resolveResult = PlaybackSourceResolver.resolve(
                context = getApplication(),
                ep = episode,
                media = media
            )

            when (resolveResult) {
                is ResolveResult.Ok -> {
                    val resolved = resolveResult.source
                    currentSourceUri = resolved.uri

                    if (castState.value.isConnected) {
                        player.pause()
                        castManager.castEpisode(
                            episode = episode,
                            mediaTitle = mediaTitle,
                            startPositionMs = resumePos
                        )
                    } else {
                        val mediaItem = MediaItem.Builder()
                            .setUri(resolved.uri)
                            .apply {
                                if (!resolved.mimeType.isNullOrBlank()) {
                                    setMimeType(resolved.mimeType)
                                }
                            }
                            .build()

                        player.stop()
                        player.setMediaItem(mediaItem)
                        if (resumePos > 0) {
                            player.seekTo(resumePos)
                        }
                        player.prepare()
                        player.play()
                    }
                    startProgressTracking()
                }
                is ResolveResult.Fail -> {
                    currentSourceUri = null
                    _uiState.value = _uiState.value.copy(
                        errorMessage = resolveResult.reason,
                        errorDetails = buildString {
                            appendLine("Erro: ${resolveResult.reason}")
                            appendLine("Episódio: ${episode.title} (ID: ${episode.id})")
                            appendLine("Mídia: $mediaTitle (ID: ${episode.mediaId})")
                            episode.streamUrl?.let { appendLine("streamUrl: $it") }
                            episode.uriString?.let { appendLine("uriString: $it") }
                            episode.filePath?.let { appendLine("filePath: $it") }
                        },
                        canRetry = false,
                        isBuffering = false
                    )
                }
            }
        }
    }

    fun playMagnetStream(magnetUri: String, title: String) {
        currentEpisode = null
        currentChannel = null
        currentMediaTitle = title
        allEpisodesInSeries = emptyList()
        currentSourceUri = null

        val parsed = TorrentUtils.parseMagnet(magnetUri)
        val displayTitle = if (title.isNotBlank()) title else parsed.name
        val ep = EpisodeEntity(
            id = "torrent_temp",
            mediaId = "torrent_temp_media",
            seasonNumber = 0,
            episodeNumber = 1,
            title = displayTitle,
            streamUrl = magnetUri
        )

        playMediaEpisode(
            episode = ep,
            mediaTitle = displayTitle,
            allEpisodes = listOf(ep)
        )
    }


    fun playLiveStream(title: String, group: String, streamUrl: String) {
        currentEpisode = null
        currentChannel = IptvChannelEntity(
            id = java.util.UUID.randomUUID().toString(),
            name = title,
            group = group,
            url = streamUrl
        )
        currentMediaTitle = title
        allEpisodesInSeries = emptyList()

        _uiState.value = PlayerUiState(
            title = title,
            subtitle = "TV Ao Vivo • $group",
            isLive = true,
            isBuffering = true,
            isCasting = castState.value.isConnected,
            castDeviceName = castState.value.deviceName
        )

        val resolveResult = PlaybackSourceResolver.resolveLive(streamUrl)
        when (resolveResult) {
            is ResolveResult.Ok -> {
                val resolved = resolveResult.source
                currentSourceUri = resolved.uri

                if (castState.value.isConnected) {
                    player.pause()
                    currentChannel?.let { castManager.castIptvChannel(it) }
                } else {
                    val mediaItem = MediaItem.Builder()
                        .setUri(resolved.uri)
                        .apply {
                            if (!resolved.mimeType.isNullOrBlank()) {
                                setMimeType(resolved.mimeType)
                            }
                        }
                        .build()

                    player.stop()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                }
                startProgressTracking()
            }
            is ResolveResult.Fail -> {
                currentSourceUri = null
                _uiState.value = _uiState.value.copy(
                    errorMessage = resolveResult.reason,
                    errorDetails = "Erro: ${resolveResult.reason}\nCanal: $title ($group)\nURL: $streamUrl",
                    canRetry = true,
                    isBuffering = false
                )
            }
        }
    }

    fun retryPlayback() {
        _uiState.value = _uiState.value.copy(errorMessage = null, errorDetails = null, isBuffering = true)
        val ep = currentEpisode
        val ch = currentChannel
        when {
            ep != null -> playMediaEpisode(ep, currentMediaTitle, allEpisodesInSeries, _uiState.value.currentPosition)
            ch != null -> playLiveStream(ch.name, ch.group, ch.url)
            else -> {
                player.prepare()
                player.play()
            }
        }
    }

    private fun sanitizeUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val userInfo = uri.userInfo
            var result = url
            if (!userInfo.isNullOrBlank()) {
                result = result.replace(userInfo, "***:***")
            }
            result.replace(Regex("(?i)(password|pass|pwd|token|key|secret)=([^&]+)"), "$1=***")
        } catch (_: Exception) {
            url
        }
    }

    fun triggerCastForCurrentMedia() {
        if (!castState.value.isConnected) return

        if (currentEpisode != null) {
            val pos = if (exoPlayer != null && exoPlayer!!.currentPosition > 0) exoPlayer!!.currentPosition else 0
            player.pause()
            castManager.castEpisode(
                episode = currentEpisode!!,
                mediaTitle = currentMediaTitle,
                startPositionMs = pos
            )
        } else if (currentChannel != null) {
            player.pause()
            castManager.castIptvChannel(currentChannel!!)
        }
    }

    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = viewModelScope.launch {
            while (isActive) {
                if (castState.value.isConnected) {
                    val cState = castState.value
                    _uiState.value = _uiState.value.copy(
                        currentPosition = cState.currentPosition,
                        duration = cState.duration,
                        isPlaying = cState.isPlaying,
                        isBuffering = cState.isBuffering
                    )

                    val ep = currentEpisode
                    if (ep != null && cState.duration > 0) {
                        val progressSec = cState.currentPosition / 1000
                        val durationSec = cState.duration / 1000
                        val isWatched = progressSec > (durationSec * 0.9)
                        repository.updatePlaybackProgress(
                            episodeId = ep.id,
                            mediaId = ep.mediaId,
                            progressSeconds = progressSec,
                            durationSeconds = durationSec,
                            watched = isWatched
                        )
                    }
                } else {
                    exoPlayer?.let { p ->
                        val pos = p.currentPosition
                        val dur = if (p.duration != C.TIME_UNSET && p.duration > 0) p.duration else 0
                        val buffered = p.bufferedPosition

                        _uiState.value = _uiState.value.copy(
                            currentPosition = pos,
                            duration = dur,
                            bufferedPosition = buffered
                        )

                        val ep = currentEpisode
                        if (ep != null && dur > 0) {
                            val progressSec = pos / 1000
                            val durationSec = dur / 1000
                            val isWatched = progressSec > (durationSec * 0.9)
                            repository.updatePlaybackProgress(
                                episodeId = ep.id,
                                mediaId = ep.mediaId,
                                progressSeconds = progressSec,
                                durationSeconds = durationSec,
                                watched = isWatched
                            )
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    fun togglePlayPause() {
        if (castState.value.isConnected) {
            castManager.togglePlayPause()
        } else {
            exoPlayer?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        if (castState.value.isConnected) {
            castManager.seekTo(positionMs)
        } else {
            exoPlayer?.seekTo(positionMs.coerceAtLeast(0))
        }
    }

    fun seekForward() {
        if (castState.value.isConnected) {
            castManager.seekForward10()
        } else {
            exoPlayer?.seekForward()
        }
    }

    fun seekBack() {
        if (castState.value.isConnected) {
            castManager.seekBack10()
        } else {
            exoPlayer?.seekBack()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        exoPlayer?.playbackParameters = PlaybackParameters(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun cycleResizeMode() {
        val current = _uiState.value.resizeMode
        val next = when (current) {
            ResizeMode.FIT -> ResizeMode.ZOOM
            ResizeMode.ZOOM -> ResizeMode.FILL
            ResizeMode.FILL -> ResizeMode.FIT
            else -> ResizeMode.FIT
        }
        _uiState.value = _uiState.value.copy(resizeMode = next)
    }

    fun playNextEpisode() {
        val currentEp = allEpisodesInSeries.find { it.id == currentEpisode?.id } ?: return
        val currentIndex = allEpisodesInSeries.indexOf(currentEp)
        if (currentIndex in 0 until (allEpisodesInSeries.size - 1)) {
            val next = allEpisodesInSeries[currentIndex + 1]
            playMediaEpisode(
                episode = next,
                mediaTitle = _uiState.value.title,
                allEpisodes = allEpisodesInSeries,
                startPositionMs = 0
            )
        }
    }

    private fun updateAvailableTracks(tracks: Tracks) {
        val audioTracks = mutableListOf<TrackInfo>()
        val subTracks = mutableListOf<TrackInfo>()

        for (group in tracks.groups) {
            val trackGroup = group.mediaTrackGroup
            val trackType = group.type

            for (i in 0 until trackGroup.length) {
                val format = trackGroup.getFormat(i)
                val isSelected = group.isTrackSelected(i)
                val label = format.label ?: format.language ?: "Faixa ${i + 1}"

                if (trackType == C.TRACK_TYPE_AUDIO) {
                    audioTracks.add(
                        TrackInfo(
                            index = audioTracks.size,
                            label = "$label (${format.sampleMimeType?.substringAfterLast('/') ?: "audio"})",
                            language = format.language,
                            isSelected = isSelected
                        )
                    )
                } else if (trackType == C.TRACK_TYPE_TEXT) {
                    subTracks.add(
                        TrackInfo(
                            index = subTracks.size,
                            label = label,
                            language = format.language,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }

        _uiState.value = _uiState.value.copy(
            availableAudioTracks = audioTracks,
            availableSubtitleTracks = subTracks
        )
    }

    fun selectAudioTrack(trackIndex: Int) {
        val p = exoPlayer ?: return
        val tracks = p.currentTracks
        var currentIndex = 0

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                val trackGroup = group.mediaTrackGroup
                for (i in 0 until trackGroup.length) {
                    if (currentIndex == trackIndex) {
                        p.trackSelectionParameters = p.trackSelectionParameters
                            .buildUpon()
                            .setOverrideForType(
                                TrackSelectionOverride(trackGroup, listOf(i))
                            )
                            .build()
                        _uiState.value = _uiState.value.copy(selectedAudioIndex = trackIndex)
                        return
                    }
                    currentIndex++
                }
            }
        }
    }

    fun selectSubtitleTrack(trackIndex: Int) {
        val p = exoPlayer ?: return
        if (trackIndex == -1) {
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            _uiState.value = _uiState.value.copy(selectedSubtitleIndex = -1)
            return
        }

        val tracks = p.currentTracks
        var currentIndex = 0

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                val trackGroup = group.mediaTrackGroup
                for (i in 0 until trackGroup.length) {
                    if (currentIndex == trackIndex) {
                        p.trackSelectionParameters = p.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(trackGroup, listOf(i))
                            )
                            .build()
                        _uiState.value = _uiState.value.copy(
                            selectedSubtitleIndex = trackIndex,
                            activeExternalSubtitleLabel = null
                        )
                        return
                    }
                    currentIndex++
                }
            }
        }
    }

    fun disableSubtitles() {
        val p = exoPlayer ?: return
        p.trackSelectionParameters = p.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
        currentVttFile = null
        currentVttContent = null
        _uiState.value = _uiState.value.copy(
            selectedSubtitleIndex = -1,
            activeExternalSubtitleLabel = null
        )
    }

    fun applyExternalSubtitle(file: File, vttContent: String, label: String) {
        currentVttFile = file
        currentVttContent = vttContent
        _uiState.value = _uiState.value.copy(
            activeExternalSubtitleLabel = label,
            selectedSubtitleIndex = -2
        )

        // Se estiver no Cast, registrar no proxy e recarregar mídia com a legenda WebVTT
        if (castState.value.isConnected && currentEpisode != null) {
            val subUrl = castManager.proxyServer.registerSubtitle(vttContent)
            val pos = _uiState.value.currentPosition
            castManager.castEpisode(
                episode = currentEpisode!!,
                mediaTitle = currentMediaTitle,
                startPositionMs = pos,
                subtitleVttUrl = subUrl
            )
            return
        }

        // Se estiver no ExoPlayer nativo, injetar a faixa de legenda WebVTT
        val sourceUri = currentSourceUri ?: return
        val currentPos = exoPlayer?.currentPosition ?: 0L
        val isPlaying = exoPlayer?.isPlaying ?: true

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage("pt-BR")
            .setLabel(label)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val newMediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        player.setMediaItem(newMediaItem, currentPos)
        player.prepare()
        if (isPlaying) player.play()
    }

    fun updateOpenSubtitlesApiKey(key: String) {
        _openSubtitlesApiKey.value = key
    }

    fun releasePlayer() {
        progressTrackingJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        castManager.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}
