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
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
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
    val isSelected: Boolean = false,
    val ipAddress: String? = null
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
    val availableDevices: List<CastDeviceInfo> = emptyList()
)

class CastManager private constructor(private val context: Context) {

    private val localServer = LocalStreamServer(context)
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector? = null

    private val _castState = MutableStateFlow(CastState())
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressPollingJob: Job? = null

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
                deviceName = session.castDevice?.friendlyName ?: "Chromecast"
            )
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            castSession = session
            val deviceName = session.castDevice?.friendlyName ?: "Chromecast"
            _castState.value = _castState.value.copy(
                isConnected = true,
                isConnecting = false,
                deviceName = deviceName
            )
            attachRemoteMediaClient(session.remoteMediaClient)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            castSession = null
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                deviceName = null
            )
        }

        override fun onSessionEnding(session: CastSession) {
            _castState.value = _castState.value.copy(isConnecting = true)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            castSession = null
            localServer.stop()
            progressPollingJob?.cancel()
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                deviceName = null,
                isPlaying = false,
                currentPosition = 0,
                duration = 0
            )
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
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            castSession = null
            _castState.value = _castState.value.copy(isConnected = false, isConnecting = false)
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            updateFromRemoteClient()
        }

        override fun onMetadataUpdated() {
            updateFromRemoteClient()
        }
    }

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshAvailableDevices()
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshAvailableDevices()
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshAvailableDevices()
        }

        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshAvailableDevices()
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            refreshAvailableDevices()
        }
    }

    fun init() {
        try {
            castContext = CastContext.getSharedInstance(context)
            castContext?.sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
            castSession = castContext?.sessionManager?.currentCastSession

            if (castSession?.isConnected == true) {
                _castState.value = _castState.value.copy(
                    isConnected = true,
                    deviceName = castSession?.castDevice?.friendlyName ?: "Chromecast"
                )
                attachRemoteMediaClient(castSession?.remoteMediaClient)
            }

            mediaRouter = MediaRouter.getInstance(context)
            routeSelector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO)
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
                .build()

            startDiscovery()
        } catch (e: Exception) {
            Log.w("CastManager", "Google Play Services Cast not available on this device", e)
        }
    }

    fun startDiscovery() {
        try {
            val router = mediaRouter ?: return
            val selector = routeSelector ?: return
            router.addCallback(
                selector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN or MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
            refreshAvailableDevices()
        } catch (e: Exception) {
            Log.e("CastManager", "Error starting discovery", e)
        }
    }

    fun stopDiscovery() {
        try {
            mediaRouter?.removeCallback(mediaRouterCallback)
        } catch (e: Exception) {
            // Ignored
        }
    }

    private fun refreshAvailableDevices() {
        val router = mediaRouter ?: return
        val selector = routeSelector
        val routes = router.routes
        val devices = routes.filter { !it.isDefault && it.isEnabled }.map { route ->
            CastDeviceInfo(
                id = route.id,
                name = route.name,
                description = route.description ?: "Smart TV / Google Cast",
                isSelected = route.isSelected
            )
        }

        val hasAvailable = devices.isNotEmpty() || (selector != null && router.isRouteAvailable(selector, MediaRouter.AVAILABILITY_FLAG_IGNORE_DEFAULT_ROUTE))

        _castState.value = _castState.value.copy(
            availableDevices = devices,
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

    fun connectByIp(ipAddress: String) {
        val cleanIp = ipAddress.trim()
        if (cleanIp.isBlank()) return
        val customDevice = CastDeviceInfo(
            id = "ip_$cleanIp",
            name = "Smart TV ($cleanIp)",
            description = "Conexão Direta via IP",
            isSelected = true,
            ipAddress = cleanIp
        )
        val currentDevices = _castState.value.availableDevices.toMutableList()
        if (currentDevices.none { it.id == customDevice.id }) {
            currentDevices.add(0, customDevice)
        }
        _castState.value = _castState.value.copy(
            availableDevices = currentDevices,
            isConnected = true,
            deviceName = customDevice.name
        )
    }

    fun disconnect() {
        try {
            castContext?.sessionManager?.endCurrentSession(true)
            mediaRouter?.unselect(MediaRouter.UNSELECT_REASON_DISCONNECTED)
            localServer.stop()
            _castState.value = _castState.value.copy(
                isConnected = false,
                isConnecting = false,
                deviceName = null
            )
        } catch (e: Exception) {
            Log.e("CastManager", "Error disconnecting Cast", e)
        }
    }

    private fun attachRemoteMediaClient(remoteMediaClient: RemoteMediaClient?) {
        if (remoteMediaClient == null) return
        remoteMediaClient.registerCallback(remoteMediaClientCallback)
        startProgressPolling(remoteMediaClient)
    }

    private fun startProgressPolling(client: RemoteMediaClient) {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            while (isActive && _castState.value.isConnected) {
                updateFromRemoteClient()
                delay(1000)
            }
        }
    }

    private fun updateFromRemoteClient() {
        val client = castSession?.remoteMediaClient ?: return
        val isPlaying = client.isPlaying
        val isBuffering = client.isBuffering
        val pos = client.approximateStreamPosition
        val dur = client.streamDuration

        _castState.value = _castState.value.copy(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            currentPosition = if (pos >= 0) pos else 0,
            duration = if (dur > 0) dur else 0
        )
    }

    /**
     * Casts a local video (MP4, MKV) or remote episode to the Chromecast receiver
     */
    fun castEpisode(
        episode: EpisodeEntity,
        mediaTitle: String,
        posterUrl: String? = null,
        startPositionMs: Long = 0,
        subtitleUri: Uri? = null
    ) {
        val client = castSession?.remoteMediaClient ?: return
        val isLocalUri = episode.uriString?.startsWith("content://") == true ||
                episode.uriString?.startsWith("file://") == true ||
                episode.filePath?.isNotBlank() == true

        val streamUrl: String
        val contentType: String

        if (isLocalUri) {
            val uri = Uri.parse(episode.uriString ?: ("file://" + episode.filePath))
            val isMkv = episode.filePath?.endsWith(".mkv", ignoreCase = true) == true ||
                    episode.uriString?.endsWith(".mkv", ignoreCase = true) == true
            contentType = if (isMkv) "video/x-matroska" else "video/mp4"

            localServer.setMedia(mediaUri = uri, subtitleUri = subtitleUri, mimeType = contentType)
            streamUrl = localServer.getStreamUrl()
        } else {
            streamUrl = episode.uriString ?: ""
            contentType = if (streamUrl.contains(".m3u8", ignoreCase = true)) "application/x-mpegURL" else "video/mp4"
        }

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, mediaTitle)
            putString(MediaMetadata.KEY_SUBTITLE, episode.title)
            if (!posterUrl.isNullOrBlank()) {
                addImage(WebImage(Uri.parse(posterUrl)))
            }
        }

        val tracks = mutableListOf<MediaTrack>()
        val subtitleUrl = localServer.getSubtitleUrl()
        if (subtitleUrl != null) {
            val subTrack = MediaTrack.Builder(1, MediaTrack.TYPE_TEXT)
                .setName("Legendas")
                .setSubtype(MediaTrack.SUBTYPE_SUBTITLES)
                .setContentId(subtitleUrl)
                .setContentType("text/vtt")
                .setLanguage("pt-BR")
                .build()
            tracks.add(subTrack)
        }

        val mediaInfo = MediaInfo.Builder(streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(metadata)
            .setMediaTracks(tracks)
            .build()

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(startPositionMs)
            .setActiveTrackIds(if (tracks.isNotEmpty()) longArrayOf(1) else null)
            .build()

        client.load(request)
        _castState.value = _castState.value.copy(
            title = mediaTitle,
            subtitle = episode.title,
            currentPosition = startPositionMs
        )
    }

    /**
     * Casts an IPTV live stream
     */
    fun castIptvChannel(channel: IptvChannelEntity) {
        val client = castSession?.remoteMediaClient ?: return

        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, channel.name)
            putString(MediaMetadata.KEY_SUBTITLE, "TV Ao Vivo • ${channel.group}")
            if (!channel.logo.isNullOrBlank()) {
                addImage(WebImage(Uri.parse(channel.logo)))
            }
        }

        val contentType = if (channel.url.contains(".m3u8", ignoreCase = true)) {
            "application/x-mpegURL"
        } else {
            "video/mp4"
        }

        val mediaInfo = MediaInfo.Builder(channel.url)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType(contentType)
            .setMetadata(metadata)
            .build()

        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build()

        client.load(request)
        _castState.value = _castState.value.copy(
            title = channel.name,
            subtitle = "TV Ao Vivo • ${channel.group}"
        )
    }

    fun togglePlayPause() {
        val client = castSession?.remoteMediaClient ?: return
        client.togglePlayback()
    }

    fun seekTo(positionMs: Long) {
        val client = castSession?.remoteMediaClient ?: return
        client.seek(positionMs)
    }

    fun seekBack10() {
        val client = castSession?.remoteMediaClient ?: return
        val newPos = (_castState.value.currentPosition - 10000).coerceAtLeast(0)
        client.seek(newPos)
    }

    fun seekForward10() {
        val client = castSession?.remoteMediaClient ?: return
        val newPos = _castState.value.currentPosition + 10000
        client.seek(newPos)
    }
}
