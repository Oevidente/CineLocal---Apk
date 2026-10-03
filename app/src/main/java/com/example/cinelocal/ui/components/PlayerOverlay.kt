package com.example.cinelocal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import android.widget.Toast
import com.example.cinelocal.data.model.TrackInfo
import com.example.cinelocal.cast.CastState
import com.example.cinelocal.player.PlayerUiState
import com.example.cinelocal.ui.theme.AccentGold
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkBackground
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun PlayerOverlay(
    uiState: PlayerUiState,
    castState: CastState,
    openSubtitlesApiKey: String = "",
    onBackClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onResizeModeCycle: () -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    onApplyExternalSubtitle: (file: File, vttContent: String, label: String) -> Unit = { _, _, _ -> },
    onDisableSubtitles: () -> Unit = {},
    onOpenSettingsForApiKey: () -> Unit = {},
    onNextEpisodeClick: () -> Unit,
    onCastClick: () -> Unit,
    onRetryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var areControlsVisible by remember { mutableStateOf(true) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    // Auto-hide controls after 4 seconds of inactivity if playing
    LaunchedEffect(areControlsVisible, uiState.isPlaying, isUserSeeking) {
        if (areControlsVisible && uiState.isPlaying && !isUserSeeking) {
            delay(4000)
            areControlsVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                areControlsVisible = !areControlsVisible
            }
    ) {
        // Casting overlay badge if active
        if (castState.isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CastConnected,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Transmitindo na TV",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = castState.deviceName ?: "Chromecast",
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentGold
                    )
                    Text(
                        text = "Use os controles abaixo ou do celular como controle remoto.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // Buffering Indicator
        if (uiState.isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = CineRed,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(56.dp)
                )
            }
        }

        // Error message overlay
        uiState.errorMessage?.let { errorMsg ->
            val context = LocalContext.current
            val clipboardManager = LocalClipboardManager.current
            val detailsText = uiState.errorDetails ?: errorMsg

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.90f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(
                        text = "Erro na Reprodução",
                        style = MaterialTheme.typography.titleLarge,
                        color = CineRed,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.canRetry) {
                            Button(
                                onClick = onRetryClick,
                                colors = ButtonDefaults.buttonColors(containerColor = CineRed)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Tentar de novo")
                            }
                        }

                        OutlinedButton(
                            onClick = { showDetailsDialog = true }
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detalhes", color = TextPrimary)
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(detailsText))
                                Toast.makeText(context, "Detalhes copiados!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copiar", color = TextPrimary)
                        }

                        TextButton(onClick = onBackClick) {
                            Text("Voltar", color = Color.White)
                        }
                    }
                }
            }
        }

        if (showDetailsDialog) {
            val context = LocalContext.current
            val clipboardManager = LocalClipboardManager.current
            val detailsText = uiState.errorDetails ?: (uiState.errorMessage ?: "Sem detalhes")

            AlertDialog(
                onDismissRequest = { showDetailsDialog = false },
                title = { Text("Detalhes Técnicos", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = detailsText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextSecondary
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showDetailsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = CineRed)
                    ) {
                        Text("Fechar")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(detailsText))
                            Toast.makeText(context, "Detalhes copiados!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Copiar Detalhes", color = TextPrimary)
                    }
                },
                containerColor = DarkBackground
            )
        }

        // Animated Controls Overlay
        AnimatedVisibility(
            visible = areControlsVisible || castState.isConnected,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Voltar",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = uiState.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (uiState.subtitle.isNotBlank()) {
                                Text(
                                    text = uiState.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (castState.isConnected) AccentGold else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Top Action Icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CastButton(
                            castState = castState,
                            onClick = onCastClick
                        )

                        // Aspect Ratio button
                        if (!castState.isConnected) {
                            IconButton(onClick = onResizeModeCycle) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = uiState.resizeMode.label,
                                    tint = Color.White
                                )
                            }

                            // Speed button
                            Box {
                                IconButton(onClick = { showSpeedMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = "Velocidade",
                                        tint = if (uiState.playbackSpeed != 1.0f) AccentGold else Color.White
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false },
                                    modifier = Modifier.background(DarkSurface)
                                ) {
                                    listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${speed}x",
                                                    color = if (uiState.playbackSpeed == speed) CineRed else TextPrimary,
                                                    fontWeight = if (uiState.playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                onSpeedChange(speed)
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Audio Tracks button
                        if (uiState.availableAudioTracks.isNotEmpty()) {
                            IconButton(onClick = { showAudioDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.Audiotrack,
                                    contentDescription = "Faixas de Áudio",
                                    tint = Color.White
                                )
                            }
                        }

                        // Subtitles button (OpenSubtitles & Embedded)
                        IconButton(onClick = { showSubtitleDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.ClosedCaption,
                                contentDescription = "Legendas e OpenSubtitles",
                                tint = if (uiState.selectedSubtitleIndex >= 0 || uiState.activeExternalSubtitleLabel != null) AccentGold else Color.White
                            )
                        }
                    }
                }

                // Center Playback Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!uiState.isLive) {
                        IconButton(
                            onClick = onSeekBack,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("seek_back_10")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Voltar 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Surface(
                        color = if (castState.isConnected) AccentGold else CineRed,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable(onClick = onPlayPauseClick)
                            .testTag("player_play_pause")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pausar" else "Reproduzir",
                                tint = if (castState.isConnected) Color.Black else Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    if (!uiState.isLive) {
                        IconButton(
                            onClick = onSeekForward,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("seek_forward_10")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Avançar 10s",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    if (uiState.hasNextEpisode) {
                        IconButton(
                            onClick = onNextEpisodeClick,
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .testTag("next_episode_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Próximo Episódio",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Bottom Bar with Timeline
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    if (!uiState.isLive && uiState.duration > 0) {
                        val currentMs = if (isUserSeeking) sliderValue.toLong() else uiState.currentPosition
                        val durationMs = uiState.duration

                        Slider(
                            value = if (isUserSeeking) sliderValue else uiState.currentPosition.toFloat().coerceIn(0f, durationMs.toFloat()),
                            onValueChange = {
                                isUserSeeking = true
                                sliderValue = it
                            },
                            onValueChangeFinished = {
                                onSeekTo(sliderValue.toLong())
                                isUserSeeking = false
                            },
                            valueRange = 0f..durationMs.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = if (castState.isConnected) AccentGold else CineRed,
                                activeTrackColor = if (castState.isConnected) AccentGold else CineRed,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp)
                                .testTag("player_seekbar")
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTime(currentMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = formatTime(durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (uiState.isTorrent) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = Color(0xFF00E676),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "TORRENT P2P",
                                        color = Color.Black,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "Streaming contínuo via Magnet Link",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    } else if (uiState.isLive) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                color = CineRed,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "AO VIVO",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = if (castState.isConnected) "Transmitindo IPTV via Google Cast" else "Transmissão contínua em tempo real",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }

    // Audio Tracks Dialog
    if (showAudioDialog) {
        AudioTracksSelectionDialog(
            audioTracks = uiState.availableAudioTracks,
            selectedAudio = uiState.selectedAudioIndex,
            onSelectAudio = onSelectAudioTrack,
            onDismiss = { showAudioDialog = false }
        )
    }

    // Subtitles & OpenSubtitles Dialog
    if (showSubtitleDialog) {
        SubtitleSelectionDialog(
            mediaTitle = uiState.title,
            openSubtitlesApiKey = openSubtitlesApiKey,
            availableSubtitleTracks = uiState.availableSubtitleTracks,
            selectedSubtitleIndex = uiState.selectedSubtitleIndex,
            activeExternalSubtitleLabel = uiState.activeExternalSubtitleLabel,
            onSelectEmbeddedTrack = onSelectSubtitleTrack,
            onApplyExternalSubtitle = onApplyExternalSubtitle,
            onDisableSubtitles = onDisableSubtitles,
            onOpenSettingsForApiKey = onOpenSettingsForApiKey,
            onDismiss = { showSubtitleDialog = false }
        )
    }
}

@Composable
fun AudioTracksSelectionDialog(
    audioTracks: List<com.example.cinelocal.data.model.TrackInfo>,
    selectedAudio: Int,
    onSelectAudio: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Faixas de Áudio",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(audioTracks) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectAudio(track.index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = track.isSelected || (selectedAudio == track.index),
                            onClick = { onSelectAudio(track.index) },
                            colors = RadioButtonDefaults.colors(selectedColor = CineRed)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = track.label, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Concluído", color = CineRed, fontWeight = FontWeight.Bold)
            }
        }
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
