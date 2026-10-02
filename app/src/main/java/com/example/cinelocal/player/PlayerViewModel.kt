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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val errorMessage: String? = null,
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

    private var progressTrackingJob: Job? = null
    private var currentEpisode: EpisodeEntity? = null
    private var currentChannel: IptvChannelEntity? = null
    private var currentMediaTitle: String = ""
    private var allEpisodesInSeries: List<EpisodeEntity> = emptyList()

    init {
        castManager.init()
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
        return ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
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
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Erro na reprodução: ${error.localizedMessage ?: "Formato não suportado"}",
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

        val isMagnet = (episode.uriString?.startsWith("magnet:", ignoreCase = true) == true) || 
                       (episode.filePath?.startsWith("magnet:", ignoreCase = true) == true)
        val parsedMagnet = if (isMagnet) TorrentUtils.parseMagnet(episode.uriString ?: episode.filePath ?: "") else null

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

        if (castState.value.isConnected) {
            player.pause()
            castManager.castEpisode(
                episode = episode,
                mediaTitle = mediaTitle,
                startPositionMs = resumePos
            )
        } else {
            val targetUriString = parsedMagnet?.streamUrl ?: episode.uriString ?: ("file://" + episode.filePath)
            val uri = Uri.parse(targetUriString)
            val mediaItem = MediaItem.fromUri(uri)

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

    fun playMagnetStream(magnetUri: String, title: String) {
        currentEpisode = null
        currentChannel = null
        currentMediaTitle = title
        allEpisodesInSeries = emptyList()

        val parsed = TorrentUtils.parseMagnet(magnetUri)
        val displayTitle = if (title.isNotBlank()) title else parsed.name

        _uiState.value = PlayerUiState(
            title = displayTitle,
            subtitle = "Torrent P2P Stream • Hash: ${parsed.infoHash.take(8)}",
            isLive = false,
            isTorrent = true,
            isBuffering = true,
            isCasting = castState.value.isConnected,
            castDeviceName = castState.value.deviceName
        )

        val targetUrl = parsed.streamUrl
        val uri = Uri.parse(targetUrl)
        val mediaItem = MediaItem.fromUri(uri)

        player.stop()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        startProgressTracking()
    }

    fun playLiveStream(title: String, group: String, streamUrl: String) {
        currentEpisode = null
        currentChannel = IptvChannelEntity(
            id = 0L,
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

        if (castState.value.isConnected) {
            player.pause()
            currentChannel?.let { castManager.castIptvChannel(it) }
        } else {
            val uri = Uri.parse(streamUrl)
            val mediaItem = MediaItem.fromUri(uri)

            player.stop()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }

        startProgressTracking()
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
                        _uiState.value = _uiState.value.copy(selectedSubtitleIndex = trackIndex)
                        return
                    }
                    currentIndex++
                }
            }
        }
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
