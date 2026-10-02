package com.example.cinelocal.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.cinelocal.player.PlayerViewModel
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
        // ExoPlayer Native View
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

        // Custom Overlay Controls
        PlayerOverlay(
            uiState = uiState,
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
            modifier = Modifier.fillMaxSize()
        )
    }
}
