package com.example.cinelocal.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.IptvChannelEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.MediaWithEpisodes
import com.example.cinelocal.ui.components.HeroBanner
import com.example.cinelocal.ui.components.IptvChannelCard
import com.example.cinelocal.ui.components.MediaRow
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    allMedia: List<MediaItemEntity>,
    movies: List<MediaItemEntity>,
    series: List<MediaItemEntity>,
    torrents: List<MediaItemEntity> = emptyList(),
    continueWatching: List<EpisodeEntity>,
    allMediaWithEpisodes: List<MediaWithEpisodes>,
    channels: List<IptvChannelEntity>,
    onMediaClick: (MediaItemEntity) -> Unit,
    onPlayMedia: (MediaItemEntity) -> Unit,
    onPlayEpisode: (EpisodeEntity, List<EpisodeEntity>, String) -> Unit,
    onPlayChannel: (IptvChannelEntity) -> Unit,
    onFavoriteToggle: (MediaItemEntity) -> Unit,
    onChannelFavoriteToggle: (IptvChannelEntity) -> Unit,
    onAddMediaClick: () -> Unit,
    onPickFilesClick: () -> Unit = {},
    onOpenPcNetwork: () -> Unit = {},
    onNavigateToMovies: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToTorrents: () -> Unit = {},
    onNavigateToChannels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val featuredItem = allMedia.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_content"),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        // Hero Banner
        if (featuredItem != null) {
            item {
                HeroBanner(
                    media = featuredItem,
                    onPlayClick = { onPlayMedia(featuredItem) },
                    onDetailsClick = { onMediaClick(featuredItem) }
                )
            }
        }

        // Continue Watching Row
        if (continueWatching.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "Continuar Assistindo",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(continueWatching, key = { it.id }) { ep ->
                            val parentMediaWithEp = allMediaWithEpisodes.find { it.media.id == ep.mediaId }
                            val mediaTitle = parentMediaWithEp?.media?.title ?: "Mídia"
                            val allEps = parentMediaWithEp?.episodes ?: listOf(ep)

                            Card(
                                modifier = Modifier
                                    .width(200.dp)
                                    .clickable { onPlayEpisode(ep, allEps, mediaTitle) }
                                    .testTag("continue_watch_${ep.id}"),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface)
                            ) {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(115.dp)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    ) {
                                        AsyncImage(
                                            model = parentMediaWithEp?.media?.backdropPath
                                                ?: parentMediaWithEp?.media?.posterPath
                                                ?: "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=400&q=80",
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable { onPlayEpisode(ep, allEps, mediaTitle) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Continuar",
                                                tint = Color.White,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        if (ep.durationSeconds > 0) {
                                            val progress = ep.progressSeconds.toFloat() / ep.durationSeconds.toFloat()
                                            LinearProgressIndicator(
                                                progress = { progress.coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .align(Alignment.BottomCenter),
                                                color = CineRed,
                                                trackColor = Color.Black.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = mediaTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = ep.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Movies Row
        if (movies.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Filmes",
                    items = movies,
                    onItemClick = onMediaClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onSeeAllClick = onNavigateToMovies
                )
            }
        }

        // Series Row
        if (series.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Séries",
                    items = series,
                    onItemClick = onMediaClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onSeeAllClick = onNavigateToSeries
                )
            }
        }

        // Torrents Row
        if (torrents.isNotEmpty()) {
            item {
                MediaRow(
                    title = "Torrents Magnet",
                    items = torrents,
                    onItemClick = onMediaClick,
                    onFavoriteToggle = onFavoriteToggle,
                    onSeeAllClick = onNavigateToTorrents
                )
            }
        }

        // Live Channels Quick Strip
        if (channels.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TV Ao Vivo",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onNavigateToChannels) {
                            Text(
                                text = "Grade Completa",
                                color = CineRed,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        channels.take(3).forEach { channel ->
                            IptvChannelCard(
                                channel = channel,
                                onPlayClick = { onPlayChannel(channel) },
                                onFavoriteToggle = { onChannelFavoriteToggle(channel) }
                            )
                        }
                    }
                }
            }
        }

        // If library is completely empty
        if (allMedia.isEmpty() && channels.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            color = CineRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = CineRed,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                        Text(
                            text = "Sua biblioteca CineLocal",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Adicione vídeos do seu celular ou conecte seu Computador / PC pela rede Wi-Fi para assistir seus filmes e séries.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onPickFilesClick,
                                colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Selecionar Vídeo(s) do Celular", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onAddMediaClick,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Mais Opções", fontSize = 12.sp, color = TextPrimary)
                                }

                                OutlinedButton(
                                    onClick = onOpenPcNetwork,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Computer,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFF38BDF8)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Conectar PC", fontSize = 12.sp, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

