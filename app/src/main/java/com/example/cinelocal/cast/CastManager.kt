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
import com.google.android.gms.cast.CastDevice
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

data class CastDeviceInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val isSelected: Boolean = false
)

data class CastState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val hasAvailableDevices: Boolean = false,
    val deviceName: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val title: String = "",
    val subtitle: String = "",
    val lastError: String? = null,
    val availableDevices: List<CastDeviceInfo> = emptyList()
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
                lastError = null
            )
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            castSession = session
            val deviceName = session.castDevice?.friendlyName ?: "Chromecast"
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                deviceName = deviceName,
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
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                deviceName = deviceName
            )
            attachRemoteMediaClient(session.remoteMediaClient)
            onSessionStateChanged?.invoke(true)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            castSession = null
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                deviceName = null
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
            router.selectRoute(route)
        }
    }

    fun disconnect() {
        try {
            castContext?.sessionManager?.endCurrentSession(true)
            mediaRouter?.unselect(MediaRouter.UNSELECT_REASON_DISCONNECTED)
            CastServerService.stop(context)
            proxyServer.stop()
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                deviceName = null
            )
        } catch (e: Exception) {
            Log.e("CastManager", "Erro ao desconectar Cast", e)
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

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val client = castSession?.remoteMediaClient ?: return
            val status = client.mediaStatus

            if (status != null) {
                if (status.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                    status.idleReason == MediaStatus.IDLE_REASON_ERROR
                ) {
                    val errorMsg = "A TV encontrou um erro ao decodificar a mídia (formato não suportado)."
                    _castState.value = _castState.value.copy(lastError = errorMsg)
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
            }
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

    fun castEpisode(
        episode: EpisodeEntity,
        media: MediaItemEntity? = null,
        mediaTitle: String,
        posterUrl: String? = null,
        startPositionMs: Long = 0,
        subtitleVttUrl: String? = null,
        onResult: ((Boolean, String?) -> Unit)? = null
    ) {
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

        if (resolved.isLocal) {
            try {
                CastServerService.start(
                    context,
                    _castState.value.deviceName ?: "Chromecast",
                    "$mediaTitle - ${episode.title}"
                )
            } catch (e: Exception) {
                Log.w("CastManager", "Aviso ao iniciar serviço de notificação do Cast (continuando transmissão): ${e.message}")
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
                _castState.value = _castState.value.copy(
                    title = mediaTitle,
                    subtitle = episode.title,
                    currentPosition = startPositionMs,
                    lastError = null
                )
                onResult?.invoke(true, null)
            } else {
                val errorMsg = "Falha ao carregar na TV (Código: ${result.status.statusCode} ${result.status.statusMessage ?: ""})"
                _castState.value = _castState.value.copy(lastError = errorMsg)
                onResult?.invoke(false, errorMsg)
            }
        }
    }

    fun castIptvChannel(channel: IptvChannelEntity, onResult: ((Boolean, String?) -> Unit)? = null) {
        val client = castSession?.remoteMediaClient
        if (client == null) {
            onResult?.invoke(false, "Nenhuma sessão ativa com o Chromecast")
            return
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, channel.name)
            putString(MediaMetadata.KEY_SUBTITLE, "Ao Vivo • ${channel.group}")
            if (!channel.logo.isNullOrBlank()) {
                addImage(WebImage(Uri.parse(channel.logo)))
            }
        }

        val contentType = CastMediaResolver.sanitizeMimeForCast(channel.url)

        val mediaInfo = MediaInfo.Builder(channel.url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        client.load(request).setResultCallback { result ->
            if (result.status.isSuccess) {
                _castState.value = _castState.value.copy(
                    title = channel.name,
                    subtitle = channel.group,
                    currentPosition = 0,
                    lastError = null
                )
                onResult?.invoke(true, null)
            } else {
                val errorMsg = "Falha ao carregar canal na TV (Código: ${result.status.statusCode})"
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
