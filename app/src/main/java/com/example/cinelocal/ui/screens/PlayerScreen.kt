package com.example.cinelocal.ui.screens

import android.app.Activity
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.cinelocal.player.PlayerViewModel
import com.example.cinelocal.ui.components.CastDeviceDialog
import com.example.cinelocal.ui.components.PlayerOverlay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val castState by playerViewModel.castState.collectAsStateWithLifecycle()

    var showCastDialog by remember { mutableStateOf(false) }

    val castPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        playerViewModel.castManager.startDiscovery()
        showCastDialog = true
    }

    fun openCastWithPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            castPermissionLauncher.launch(
                arrayOf(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            )
        } else {
            castPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    BackHandler {
        onBackClick()
    }

    // Keep screen on during playback
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_screen_container")
    ) {
        // ExoPlayer Native View (active when not casting or paused during cast)
        if (!castState.isConnected) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        player = playerViewModel.player
                        resizeMode = uiState.resizeMode.mode
                    }
                },
                update = { playerView ->
                    playerView.player = playerViewModel.player
                    playerView.resizeMode = uiState.resizeMode.mode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Custom Overlay Controls
        PlayerOverlay(
            uiState = uiState,
            castState = castState,
            onBackClick = onBackClick,
            onPlayPauseClick = { playerViewModel.togglePlayPause() },
            onSeekBack = { playerViewModel.seekBack() },
            onSeekForward = { playerViewModel.seekForward() },
            onSeekTo = { pos -> playerViewModel.seekTo(pos) },
            onSpeedChange = { speed -> playerViewModel.setPlaybackSpeed(speed) },
            onResizeModeCycle = { playerViewModel.cycleResizeMode() },
            onSelectAudioTrack = { idx -> playerViewModel.selectAudioTrack(idx) },
            onSelectSubtitleTrack = { idx -> playerViewModel.selectSubtitleTrack(idx) },
            onNextEpisodeClick = { playerViewModel.playNextEpisode() },
            onCastClick = {
                openCastWithPermissions()
            },
            onRetryClick = { playerViewModel.retryPlayback() },
            modifier = Modifier.fillMaxSize()
        )

        if (showCastDialog) {
            CastDeviceDialog(
                castState = castState,
                onSelectDevice = { routeId ->
                    playerViewModel.castManager.selectDevice(routeId)
                    playerViewModel.triggerCastForCurrentMedia()
                },
                onConnectByIp = { ip ->
                    playerViewModel.castManager.connectByIp(ip)
                    playerViewModel.triggerCastForCurrentMedia()
                },
                onRefreshDiscovery = {
                    playerViewModel.castManager.startDiscovery()
                },
                onDisconnect = { playerViewModel.castManager.disconnect() },
                onDismiss = { showCastDialog = false }
            )
        }
    }
}
