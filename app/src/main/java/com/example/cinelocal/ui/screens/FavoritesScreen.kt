package com.example.cinelocal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cinelocal.data.model.IptvChannelEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.ui.components.IptvChannelCard
import com.example.cinelocal.ui.components.MediaRow
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary

@Composable
fun FavoritesScreen(
    favoriteMedia: List<MediaItemEntity>,
    favoriteChannels: List<IptvChannelEntity>,
    onMediaClick: (MediaItemEntity) -> Unit,
    onFavoriteMediaToggle: (MediaItemEntity) -> Unit,
    onPlayChannel: (IptvChannelEntity) -> Unit,
    onChannelFavoriteToggle: (IptvChannelEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (favoriteMedia.isEmpty() && favoriteChannels.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Você ainda não favoritou nenhum filme, série ou canal de TV.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("favorites_screen"),
            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
        ) {
            if (favoriteMedia.isNotEmpty()) {
                item {
                    MediaRow(
                        title = "Filmes e Séries Favoritos",
                        items = favoriteMedia,
                        onItemClick = onMediaClick,
                        onFavoriteToggle = onFavoriteMediaToggle
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (favoriteChannels.isNotEmpty()) {
                item {
                    Text(
                        text = "Canais de TV Favoritos",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                items(favoriteChannels, key = { it.id }) { channel ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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
}
