package com.example.cinelocal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cinelocal.BuildConfig
import com.example.cinelocal.ui.theme.AccentGold
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    tmdbApiKey: String,
    openSubtitlesApiKey: String = "",
    openSubtitlesUsername: String = "",
    totalMediaCount: Int,
    totalChannelCount: Int,
    isDeveloperModeEnabled: Boolean = false,
    onToggleDeveloperMode: (Boolean) -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenTmdbConfig: () -> Unit,
    onOpenOpenSubtitlesConfig: () -> Unit = {},
    onOpenIptvManager: () -> Unit,
    onOpenPcNetwork: () -> Unit = {},
    onRescanLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    var autoPlayNext by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Metadados & APIs",
                style = MaterialTheme.typography.titleMedium,
                color = CineRed,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            SettingsItemCard(
                icon = Icons.Default.Movie,
                title = "TheMovieDB (TMDb)",
                subtitle = if (tmdbApiKey.isNotBlank()) "Configurado (Chave ativa)" else "Chave não configurada (Metadados locais)",
                badge = if (tmdbApiKey.isNotBlank()) "ATIVO" else null,
                badgeColor = if (tmdbApiKey.isNotBlank()) Color(0xFF4CAF50) else null,
                onClick = onOpenTmdbConfig,
                testTag = "settings_tmdb_item"
            )
        }

        item {
            val isConfigured = openSubtitlesApiKey.isNotBlank()
            val subText = when {
                isConfigured && openSubtitlesUsername.isNotBlank() -> "Configurado ($openSubtitlesUsername)"
                isConfigured -> "Chave configurada (sem conta vinculada)"
                else -> "Chave/Conta não configuradas"
            }
            SettingsItemCard(
                icon = Icons.Default.Subtitles,
                title = "OpenSubtitles.com v1",
                subtitle = subText,
                badge = if (isConfigured) "ATIVO" else null,
                badgeColor = if (isConfigured) Color(0xFF4CAF50) else null,
                onClick = onOpenOpenSubtitlesConfig,
                testTag = "settings_opensubtitles_item"
            )
        }

        item {
            Text(
                text = "TV Ao Vivo & Transmissões",
                style = MaterialTheme.typography.titleMedium,
                color = CineRed,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            SettingsItemCard(
                icon = Icons.Default.Tv,
                title = "Gerenciar Listas IPTV",
                subtitle = "$totalChannelCount canais cadastrados na grade",
                onClick = onOpenIptvManager,
                testTag = "settings_iptv_item"
            )
        }

        item {
            Text(
                text = "Biblioteca & Armazenamento",
                style = MaterialTheme.typography.titleMedium,
                color = CineRed,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            SettingsItemCard(
                icon = Icons.Default.Computer,
                title = "Armazenamento no Computador (PC / SMB)",
                subtitle = "Conecte seu PC para explorar e assistir pastas pela rede Wi-Fi",
                onClick = onOpenPcNetwork,
                testTag = "settings_pc_network_item"
            )
        }

        item {
            SettingsItemCard(
                icon = Icons.Default.Refresh,
                title = "Re-escanear Biblioteca Local",
                subtitle = "$totalMediaCount mídias locais cadastradas",
                onClick = onRescanLibrary,
                testTag = "settings_rescan_item"
            )
        }

        item {
            Text(
                text = "Diagnóstico & Desenvolvedor",
                style = MaterialTheme.typography.titleMedium,
                color = CineRed,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = CineRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Modo Desenvolvedor",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Habilita a tela de diagnóstico e logs do servidor HTTP e Cast em tempo real",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Switch(
                            checked = isDeveloperModeEnabled,
                            onCheckedChange = onToggleDeveloperMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CineRed
                            )
                        )
                    }

                    if (isDeveloperModeEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onOpenDiagnostics,
                            colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Abrir Diagnóstico do Cast & Servidor (Logs)", color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Player de Vídeo",
                style = MaterialTheme.typography.titleMedium,
                color = CineRed,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = CineRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Reproduzir Próximo Episódio",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Avança automaticamente ao terminar o episódio atual",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = autoPlayNext,
                        onCheckedChange = { autoPlayNext = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CineRed
                        )
                    )
                }
            }
        }

        item {
            Text(
                text = "Sobre o CineLocal",
                style = MaterialTheme.typography.titleMedium,
                color = CineRed,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "CineLocal v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Build #${BuildConfig.BUILD_NUMBER} (${BuildConfig.GIT_SHA})",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGold,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Central de mídia offline nativa para Android com suporte a ExoPlayer Media3, Google Cast, IPTV M3U e TheMovieDB.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color? = null,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CineRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (badge != null && badgeColor != null) {
                            Surface(
                                color = badgeColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = badge,
                                    color = badgeColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}
