package com.example.cinelocal.cast

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.IptvChannelEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

data class LoadInfo(
    val id: String,
    val url: String,
    val startedAt: Long,
    val isRetry: Boolean = false,
    val title: String = "",
    val subtitle: String = ""
)

data class CastDeviceInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val isSelected: Boolean = false
)

data class CastPlaybackErrorInfo(
    val title: String,
    val subtitle: String? = null,
    val message: String,
    val technicalDetails: String? = null,
    val positionMs: Long = 0L,
    val episode: EpisodeEntity? = null,
    val media: MediaItemEntity? = null,
    val channel: IptvChannelEntity? = null
)

data class CastState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val hasAvailableDevices: Boolean = false,
    val deviceName: String? = null,
    val deviceModel: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val title: String = "",
    val subtitle: String = "",
    val lastError: String? = null,
    val playbackError: CastPlaybackErrorInfo? = null,
    val availableDevices: List<CastDeviceInfo> = emptyList(),
    val compatibilityWarning: String? = null,
    val showIncompatibleDialog: Boolean = false
)

class CastManager private constructor(private val context: Context) {

    val proxyServer = MediaProxyServer(context)
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector? = null

    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressPollingJob: Job? = null

    var onSessionStateChanged: ((Boolean) -> Unit)? = null
    var onLoadResult: ((Boolean, String?) -> Unit)? = null

    private val disconnecting = AtomicBoolean(false)
    private var currentLoad: LoadInfo? = null
    private var lastProbeResult: MediaProbeResult? = null

    private var pendingIncompatibleAction: (() -> Unit)? = null

    companion object {
        @Volatile
        private var instance: CastManager? = null

        fun getInstance(context: Context): CastManager {
            return instance ?: synchronized(this) {
                instance ?: CastManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _castState.value = _castState.value.copy(
                isConnecting = true,
                deviceName = session.castDevice?.friendlyName ?: "Chromecast",
                deviceModel = session.castDevice?.modelName,
                lastError = null
            )
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            castSession = session
            val deviceName = session.castDevice?.friendlyName ?: "Chromecast"
            val deviceModel = session.castDevice?.modelName
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                deviceName = deviceName,
                deviceModel = deviceModel,
                lastError = null
            )
            attachRemoteMediaClient(session.remoteMediaClient)
            onSessionStateChanged?.invoke(true)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            castSession = null
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                deviceName = null,
                deviceModel = null,
                lastError = "Falha ao iniciar sessão Cast (Erro $error)"
            )
            onSessionStateChanged?.invoke(false)
        }

        override fun onSessionEnding(session: CastSession) {
            _castState.value = _castState.value.copy(isConnecting = true)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            val lastPos = _castState.value.currentPosition
            castSession = null
            CastServerService.stop(context)
            proxyServer.stop()
            progressPollingJob?.cancel()
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                deviceName = null,
                deviceModel = null,
                isPlaying = false,
                currentPosition = lastPos
            )
            onSessionStateChanged?.invoke(false)
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _castState.value = _castState.value.copy(isConnecting = true)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            castSession = session
            val deviceName = session.castDevice?.friendlyName ?: "Chromecast"
            val deviceModel = session.castDevice?.modelName
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                deviceName = deviceName,
                deviceModel = deviceModel
            )
            attachRemoteMediaClient(session.remoteMediaClient)
            onSessionStateChanged?.invoke(true)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            castSession = null
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                deviceName = null,
                deviceModel = null
            )
            onSessionStateChanged?.invoke(false)
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _castState.value = _castState.value.copy(isConnecting = true)
        }
    }

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateAvailableRoutes(router)
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateAvailableRoutes(router)
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateAvailableRoutes(router)
        }

        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateAvailableRoutes(router)
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            updateAvailableRoutes(router)
        }
    }

    fun init() {
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext?.sessionManager?.addSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )

            mediaRouter = MediaRouter.getInstance(context)
            routeSelector = MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .build()

            startDiscovery()
        } catch (e: Exception) {
            Log.e("CastManager", "Erro ao inicializar Google Cast", e)
            _castState.value = _castState.value.copy(
                lastError = "Google Play Services ou Cast indisponível neste dispositivo."
            )
        }
    }

    fun startDiscovery() {
        try {
            mediaRouter?.let { router ->
                routeSelector?.let { selector ->
                    router.addCallback(
                        selector,
                        mediaRouterCallback,
                        MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
                    )
                    updateAvailableRoutes(router)
                }
            }
        } catch (e: Exception) {
            Log.e("CastManager", "Erro na descoberta de rotas Cast", e)
        }
    }

    private fun updateAvailableRoutes(router: MediaRouter) {
        val routes = router.routes
        val available = mutableListOf<CastDeviceInfo>()
        var hasAvailable = false

        for (route in routes) {
            val isCastRoute = route.matchesSelector(routeSelector ?: return) ||
                route.description?.contains("Chromecast", ignoreCase = true) == true ||
                route.description?.contains("Google Cast", ignoreCase = true) == true ||
                route.name.contains("TV", ignoreCase = true)

            if (isCastRoute && !route.isDefault) {
                hasAvailable = true
                available.add(
                    CastDeviceInfo(
                        id = route.id,
                        name = route.name,
                        description = route.description,
                        isSelected = route.isSelected
                    )
                )
            }
        }

        _castState.value = _castState.value.copy(
            availableDevices = available,
            hasAvailableDevices = hasAvailable
        )
    }

    fun selectDevice(routeId: String) {
        val router = mediaRouter ?: return
        val route = router.routes.find { it.id == routeId }
        if (route != null) {
            if (castContext?.sessionManager?.currentCastSession != null && !_castState.value.isConnected) {
                try {
                    castContext?.sessionManager?.endCurrentSession(true)
                } catch (_: Exception) {}
            }
            proxyServer.ensureStarted()
            router.selectRoute(route)
        }
    }

    fun disconnect() {
        if (!disconnecting.compareAndSet(false, true)) return
        try {
            verificationJob?.cancel()
            verificationJob = null
            currentLoad = null
            castContext?.sessionManager?.endCurrentSession(true)
            mediaRouter?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
            CastServerService.stop(context)
            proxyServer.stop()
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                isPlaying = false,
                isBuffering = false,
                deviceName = null,
                deviceModel = null,
                playbackError = null,
                showIncompatibleDialog = false
            )
        } catch (e: Exception) {
            Log.e("CastManager", "Erro ao desconectar Cast", e)
        } finally {
            disconnecting.set(false)
        }
    }

    fun restartCastServiceAndDiscovery() {
        scope.launch {
            try {
                proxyServer.log("Reiniciando serviço Cast e descoberta de rotas...")
                verificationJob?.cancel()
                verificationJob = null
                currentLoad = null
                castContext?.sessionManager?.endCurrentSession(true)
                mediaRouter?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
                CastServerService.stop(context)
                proxyServer.stop()

                mediaRouter?.let { router ->
                    routeSelector?.let { selector ->
                        router.removeCallback(mediaRouterCallback)
                        delay(300)
                        router.addCallback(
                            selector,
                            mediaRouterCallback,
                            MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
                        )
                    }
                }

                proxyServer.start()
                mediaRouter?.let { updateAvailableRoutes(it) }
                proxyServer.log("Servidor e descoberta do Cast reiniciados com sucesso.")
            } catch (e: Exception) {
                Log.e("CastManager", "Erro ao reiniciar Cast e descoberta", e)
            }
        }
    }

    fun hasActiveSession(): Boolean {
        return castSession?.isConnected == true && castSession?.remoteMediaClient != null
    }

    private fun attachRemoteMediaClient(remoteMediaClient: RemoteMediaClient?) {
        if (remoteMediaClient == null) return
        remoteMediaClient.registerCallback(remoteMediaClientCallback)
        startProgressPolling()
    }

    private var lastCastEpisode: EpisodeEntity? = null
    private var lastCastMedia: MediaItemEntity? = null
    private var lastCastChannel: IptvChannelEntity? = null
    private var lastCastTitle: String = ""
    private var lastCastSubtitle: String = ""
    private var lastCastStartPosition: Long = 0L

    var onRequestResumeLocally: ((EpisodeEntity?, IptvChannelEntity?, String, Long) -> Unit)? = null

    fun clearPlaybackError() {
        _castState.value = _castState.value.copy(playbackError = null, lastError = null)
    }

    fun dismissIncompatibleDialog() {
        _castState.value = _castState.value.copy(showIncompatibleDialog = false, compatibilityWarning = null)
        pendingIncompatibleAction = null
    }

    fun proceedCastingIncompatible() {
        _castState.value = _castState.value.copy(showIncompatibleDialog = false, compatibilityWarning = null)
        val action = pendingIncompatibleAction
        pendingIncompatibleAction = null
        action?.invoke()
    }

    fun resumeLocally() {
        val err = _castState.value.playbackError
        val ep = err?.episode ?: lastCastEpisode
        val ch = err?.channel ?: lastCastChannel
        val title = err?.title ?: lastCastTitle
        val pos = if ((err?.positionMs ?: 0L) > 0) err!!.positionMs else (_castState.value.currentPosition.takeIf { it > 0 } ?: lastCastStartPosition)

        clearPlaybackError()
        dismissIncompatibleDialog()
        disconnect()
        onRequestResumeLocally?.invoke(ep, ch, title, pos)
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = castSession?.remoteMediaClient ?: return
            val status = client.mediaStatus ?: return

            if (status.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                status.idleReason == MediaStatus.IDLE_REASON_ERROR
            ) {
                val statusContentId = status.mediaInfo?.contentId
                val activeLoad = currentLoad

                if (activeLoad != null && !statusContentId.isNullOrBlank() && statusContentId != activeLoad.url) {
                    proxyServer.log("[CAST] Status IDLE_REASON_ERROR ignorado pois pertence a um item anterior ($statusContentId vs ${activeLoad.url})")
                    return
                }

                val now = System.currentTimeMillis()
                if (activeLoad != null && !activeLoad.isRetry && (now - activeLoad.startedAt < 5000L)) {
                    proxyServer.log("[AUTO-RETRY CAST] Erro no receptor em <5s (${now - activeLoad.startedAt}ms). Reenviando LOAD uma vez (1.5s)...")
                    scope.launch {
                        delay(1500)
                        retryCurrentLoad(activeLoad)
                    }
                    return
                }

                val devModel = castSession?.castDevice?.modelName ?: _castState.value.deviceName ?: "Chromecast"
                val probe = lastProbeResult
                val techDetails = if (probe != null && probe.summary.isNotBlank()) {
                    "${probe.summary} · Aparelho: $devModel"
                } else {
                    "Formato não suportado por este aparelho (codec, perfil ou áudio). Aparelho: $devModel"
                }

                val errorTitle = "A TV não conseguiu reproduzir este arquivo"
                val errorMsg = "O receptor $devModel encerrou a reprodução por incompatibilidade de formato."

                _castState.value = _castState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    lastError = errorMsg,
                    playbackError = CastPlaybackErrorInfo(
                        title = lastCastTitle.ifBlank { _castState.value.title },
                        subtitle = lastCastSubtitle.ifBlank { _castState.value.subtitle },
                        message = errorTitle,
                        technicalDetails = techDetails,
                        positionMs = lastCastStartPosition,
                        episode = lastCastEpisode,
                        media = lastCastMedia,
                        channel = lastCastChannel
                    )
                )
                onLoadResult?.invoke(false, errorMsg)
            }

            val isPlaying = status.playerState == MediaStatus.PLAYER_STATE_PLAYING
            val isBuffering = status.playerState == MediaStatus.PLAYER_STATE_BUFFERING
            val currentPos = client.approximateStreamPosition
            val duration = client.streamDuration

            _castState.value = _castState.value.copy(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPosition = if (currentPos > 0) currentPos else 0,
                duration = if (duration > 0) duration else 0
            )

            if (_castState.value.isConnected) {
                CastServerService.start(
                    context,
                    _castState.value.deviceName ?: "Chromecast",
                    _castState.value.title.ifBlank { "Reproduzindo na TV" },
                    isPlaying = isPlaying
                )
            }
        }
    }

    private fun retryCurrentLoad(load: LoadInfo) {
        val ep = lastCastEpisode
        val ch = lastCastChannel
        currentLoad = load.copy(isRetry = true, startedAt = System.currentTimeMillis())

        if (ep != null) {
            castEpisodeInternal(
                episode = ep,
                media = lastCastMedia,
                mediaTitle = lastCastTitle,
                startPositionMs = lastCastStartPosition,
                isRetry = true
            )
        } else if (ch != null) {
            castIptvChannelInternal(
                channel = ch,
                isRetry = true
            )
        }
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            while (isActive) {
                val client = castSession?.remoteMediaClient
                if (client != null && castSession?.isConnected == true) {
                    val pos = client.approximateStreamPosition
                    val dur = client.streamDuration
                    _castState.value = _castState.value.copy(
                        currentPosition = if (pos > 0) pos else 0,
                        duration = if (dur > 0) dur else 0,
                        isPlaying = client.isPlaying
                    )
                }
                delay(1000)
            }
        }
    }

    private var verificationJob: Job? = null

    private fun verifyPlaybackStarted(
        isLive: Boolean,
        url: String,
        mimeType: String,
        mediaTitle: String,
        subtitle: String,
        startPositionMs: Long = 0,
        timeoutSeconds: Int = if (isLive) 25 else 20,
        onResult: ((Boolean, String?) -> Unit)?
    ) {
        verificationJob?.cancel()
        verificationJob = scope.launch {
            val startTime = System.currentTimeMillis()
            val timeoutMs = timeoutSeconds * 1000L
            var reported = false

            proxyServer.log("Iniciando verificação de reprodução remota no Chromecast (timeout: ${timeoutSeconds}s) -> $url ($mimeType)")

            while (isActive && System.currentTimeMillis() - startTime < timeoutMs) {
                val client = castSession?.remoteMediaClient
                if (client == null || castSession?.isConnected != true) {
                    proxyServer.log("Sessão Cast perdida durante verificação de reprodução.")
                    onResult?.invoke(false, "Sessão com o Chromecast desconectada")
                    reported = true
                    break
                }

                val status = client.mediaStatus
                val playerState = status?.playerState ?: MediaStatus.PLAYER_STATE_UNKNOWN
                val idleReason = status?.idleReason ?: MediaStatus.IDLE_REASON_NONE
                val pos = client.approximateStreamPosition
                val dur = client.streamDuration

                proxyServer.log("[STATUS CAST] playerState=$playerState idleReason=$idleReason pos=$pos dur=$dur url=$url")

                if (playerState == MediaStatus.PLAYER_STATE_PLAYING) {
                    proxyServer.log("[SUCESSO CAST] Reprodução ativa confirmada (PLAYING) no receptor!")
                    _castState.value = _castState.value.copy(
                        title = mediaTitle,
                        subtitle = subtitle,
                        isPlaying = true,
                        isBuffering = false,
                        currentPosition = if (pos > 0) pos else startPositionMs,
                        duration = if (dur > 0) dur else 0,
                        lastError = null,
                        playbackError = null
                    )
                    onResult?.invoke(true, null)
                    reported = true
                    break
                }

                if (playerState == MediaStatus.PLAYER_STATE_IDLE && idleReason == MediaStatus.IDLE_REASON_ERROR) {
                    val statusContentId = status?.mediaInfo?.contentId
                    val activeLoad = currentLoad
                    if (activeLoad != null && !statusContentId.isNullOrBlank() && statusContentId != activeLoad.url) {
                        proxyServer.log("[CAST VERIFY] Status IDLE_REASON_ERROR ignorado (mídia anterior $statusContentId vs $url)")
                    } else {
                        val devModel = castSession?.castDevice?.modelName ?: _castState.value.deviceName ?: "Chromecast"
                        val probe = lastProbeResult
                        val techDetails = if (probe != null && probe.summary.isNotBlank()) {
                            "${probe.summary} · Aparelho: $devModel"
                        } else {
                            "Formato não suportado por este aparelho (codec, perfil ou áudio). Aparelho: $devModel"
                        }
                        val errorTitle = "A TV não conseguiu reproduzir este arquivo"
                        val errorMsg = "O receptor $devModel encerrou a reprodução por incompatibilidade de formato."

                        proxyServer.log("[ERRO CAST] Receptor retornou IDLE com IDLE_REASON_ERROR")
                        _castState.value = _castState.value.copy(
                            isPlaying = false,
                            isBuffering = false,
                            lastError = errorMsg,
                            playbackError = CastPlaybackErrorInfo(
                                title = mediaTitle,
                                subtitle = subtitle,
                                message = errorTitle,
                                technicalDetails = techDetails,
                                positionMs = startPositionMs,
                                episode = lastCastEpisode,
                                media = lastCastMedia,
                                channel = lastCastChannel
                            )
                        )
                        onResult?.invoke(false, errorMsg)
                        reported = true
                        break
                    }
                }

                if (playerState == MediaStatus.PLAYER_STATE_BUFFERING) {
                    _castState.value = _castState.value.copy(isBuffering = true)
                }

                delay(1000)
            }

            if (!reported) {
                val client = castSession?.remoteMediaClient
                val lastState = client?.mediaStatus?.playerState ?: "desconhecido"
                val errorMsg = "Tempo limite excedido aguardando início da reprodução na TV (último estado: $lastState)."
                proxyServer.log("[TIMEOUT CAST] $errorMsg")
                _castState.value = _castState.value.copy(
                    isPlaying = false,
                    isBuffering = false,
                    lastError = errorMsg
                )
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun castEpisode(
        episode: EpisodeEntity,
        media: MediaItemEntity? = null,
        mediaTitle: String,
        posterUrl: String? = null,
        startPositionMs: Long = 0,
        subtitleVttUrl: String? = null,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        scope.launch {
            val resolved = CastMediaResolver.resolveForCast(context, episode, media, proxyServer)
            if (resolved == null) {
                onResult?.invoke(false, "Não foi possível resolver a fonte para transmissão na TV")
                return@launch
            }

            val probeUri = try {
                var urlStr = resolved.url
                val localIp = proxyServer.getDeviceIpAddress()
                if (localIp.isNotBlank() && urlStr.contains(localIp)) {
                    urlStr = urlStr.replace(localIp, "127.0.0.1")
                }
                Uri.parse(urlStr)
            } catch (_: Exception) {
                Uri.parse(resolved.url)
            }

            val probeResult = MediaProbe.probeMedia(context, probeUri)
            lastProbeResult = probeResult

            val deviceModel = castSession?.castDevice?.modelName
            val deviceName = castSession?.castDevice?.friendlyName

            val compatResult = CastCompatibility.checkCompatibility(deviceModel, deviceName, probeResult)

            if (!compatResult.isCompatible && compatResult.warningMessage != null) {
                pendingIncompatibleAction = {
                    castEpisodeInternal(episode, media, mediaTitle, posterUrl, startPositionMs, subtitleVttUrl, false, onResult)
                }
                _castState.value = _castState.value.copy(
                    showIncompatibleDialog = true,
                    compatibilityWarning = compatResult.warningMessage
                )
                return@launch
            }

            castEpisodeInternal(episode, media, mediaTitle, posterUrl, startPositionMs, subtitleVttUrl, false, onResult)
        }
    }

    private fun castEpisodeInternal(
        episode: EpisodeEntity,
        media: MediaItemEntity? = null,
        mediaTitle: String,
        posterUrl: String? = null,
        startPositionMs: Long = 0,
        subtitleVttUrl: String? = null,
        isRetry: Boolean = false,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        lastCastEpisode = episode
        lastCastMedia = media
        lastCastChannel = null
        lastCastTitle = mediaTitle
        lastCastSubtitle = episode.title
        lastCastStartPosition = startPositionMs

        val client = castSession?.remoteMediaClient
        if (client == null) {
            onResult?.invoke(false, "Nenhuma sessão ativa com o Chromecast")
            return
        }

        val resolved = CastMediaResolver.resolveForCast(context, episode, media, proxyServer)
        if (resolved == null) {
            onResult?.invoke(false, "Não foi possível resolver a fonte para transmissão na TV")
            return
        }

        val loadId = java.util.UUID.randomUUID().toString()
        currentLoad = LoadInfo(loadId, resolved.url, System.currentTimeMillis(), isRetry, mediaTitle, episode.title)

        if (resolved.isLocal) {
            try {
                CastServerService.start(
                    context,
                    _castState.value.deviceName ?: "Chromecast",
                    "$mediaTitle - ${episode.title}"
                )
            } catch (e: Exception) {
                Log.w("CastManager", "Aviso ao iniciar serviço do Cast: ${e.message}")
            }
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, mediaTitle)
            putString(MediaMetadata.KEY_SUBTITLE, episode.title)
            if (!posterUrl.isNullOrBlank()) {
                addImage(WebImage(Uri.parse(posterUrl)))
            }
        }

        val tracks = mutableListOf<MediaTrack>()
        if (!subtitleVttUrl.isNullOrBlank()) {
            val subTrack = MediaTrack.Builder(1, MediaTrack.TYPE_TEXT)
                .setName("Legendas")
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(subtitleVttUrl)
                .setContentType("text/vtt")
                .setLanguage("pt-BR")
                .build()
            tracks.add(subTrack)
        }

        val mediaInfo = MediaInfo.Builder(resolved.url)
            .setStreamType(resolved.streamType)
            .setContentType(resolved.mimeType)
            .setMetadata(metadata)
            .setMediaTracks(tracks)
            .build()

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(startPositionMs)
            .setActiveTrackIds(if (tracks.isNotEmpty()) longArrayOf(1) else null)
            .build()

        client.load(request).setResultCallback { result ->
            if (result.status.isSuccess) {
                proxyServer.log("Comando LOAD aceito pelo Chromecast. Aguardando PLAYER_STATE_PLAYING...")
                _castState.value = _castState.value.copy(
                    title = mediaTitle,
                    subtitle = episode.title,
                    currentPosition = startPositionMs,
                    isBuffering = true,
                    lastError = null
                )
                verifyPlaybackStarted(
                    isLive = resolved.streamType == MediaInfo.STREAM_TYPE_LIVE,
                    url = resolved.url,
                    mimeType = resolved.mimeType,
                    mediaTitle = mediaTitle,
                    subtitle = episode.title,
                    startPositionMs = startPositionMs,
                    timeoutSeconds = 20,
                    onResult = onResult
                )
            } else {
                val errorMsg = "Falha ao carregar na TV (Código: ${result.status.statusCode} ${result.status.statusMessage ?: ""})"
                _castState.value = _castState.value.copy(lastError = errorMsg)
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun castIptvChannel(channel: IptvChannelEntity, onResult: ((Boolean, String?) -> Unit)? = null) {
        castIptvChannelInternal(channel, false, onResult)
    }

    private fun castIptvChannelInternal(
        channel: IptvChannelEntity,
        isRetry: Boolean = false,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
        lastCastEpisode = null
        lastCastMedia = null
        lastCastChannel = channel
        lastCastTitle = channel.name
        lastCastSubtitle = channel.group
        lastCastStartPosition = 0L
        lastProbeResult = null

        val client = castSession?.remoteMediaClient
        if (client == null) {
            val err = "Nenhuma sessão ativa com o Chromecast"
            proxyServer.log("Falha ao transmitir IPTV: $err")
            onResult?.invoke(false, err)
            return
        }

        val resolved = CastMediaResolver.resolveIptvForCast(channel.url, proxyServer)
        proxyServer.log("Transmitindo canal IPTV '${channel.name}' -> ${resolved.url} (${resolved.mimeType})")

        val loadId = java.util.UUID.randomUUID().toString()
        currentLoad = LoadInfo(loadId, resolved.url, System.currentTimeMillis(), isRetry, channel.name, channel.group)

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, channel.name)
            putString(MediaMetadata.KEY_SUBTITLE, "Ao Vivo • ${channel.group}")
            if (!channel.logo.isNullOrBlank()) {
                addImage(WebImage(Uri.parse(channel.logo)))
            }
        }

        val mediaInfo = MediaInfo.Builder(resolved.url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(resolved.mimeType)
            .setMetadata(metadata)
            .build()

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        client.load(request).setResultCallback { result ->
            if (result.status.isSuccess) {
                proxyServer.log("Comando de carregar canal IPTV aceito pelo Chromecast. Aguardando PLAYER_STATE_PLAYING...")
                _castState.value = _castState.value.copy(
                    title = channel.name,
                    subtitle = channel.group,
                    currentPosition = 0,
                    isBuffering = true,
                    lastError = null
                )
                verifyPlaybackStarted(
                    isLive = true,
                    url = resolved.url,
                    mimeType = resolved.mimeType,
                    mediaTitle = channel.name,
                    subtitle = channel.group,
                    startPositionMs = 0L,
                    timeoutSeconds = 25,
                    onResult = onResult
                )
            } else {
                val errorMsg = "Falha ao carregar canal na TV (Código: ${result.status.statusCode} ${result.status.statusMessage ?: ""})"
                proxyServer.log("ERRO CAST: $errorMsg")
                _castState.value = _castState.value.copy(lastError = errorMsg)
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun castPublicTestVideo() {
        val client = castSession?.remoteMediaClient ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, "Big Buck Bunny (Teste Cast)")
            putString(MediaMetadata.KEY_SUBTITLE, "Stream Público de Teste")
        }
        val mediaInfo = MediaInfo.Builder("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType("video/mp4")
            .setMetadata(metadata)
            .build()
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()
        client.load(request)
    }

    fun togglePlayPause() {
        val client = castSession?.remoteMediaClient ?: return
        if (client.isPlaying) {
            client.pause()
        } else {
            client.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val client = castSession?.remoteMediaClient ?: return
        client.seek(positionMs.coerceAtLeast(0))
    }

    fun seekForward10() {
        val client = castSession?.remoteMediaClient ?: return
        val newPos = client.approximateStreamPosition + 10000
        client.seek(newPos)
    }

    fun seekBack10() {
        val client = castSession?.remoteMediaClient ?: return
        val newPos = (client.approximateStreamPosition - 10000).coerceAtLeast(0)
        client.seek(newPos)
    }

    fun getDeviceIpAddress(): String = proxyServer.getDeviceIpAddress()
    fun isProxyRunning(): Boolean = proxyServer.isRunning
    fun getProxyPort(): Int = proxyServer.port
    fun getProxyLogs(): List<String> = proxyServer.recentLogs.toList()
}
