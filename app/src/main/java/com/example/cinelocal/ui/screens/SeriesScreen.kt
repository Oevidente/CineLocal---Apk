package com.example.cinelocal.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.ui.components.MediaCard
import com.example.cinelocal.ui.theme.TextSecondary

@Composable
fun SeriesScreen(
    series: List<MediaItemEntity>,
    onSeriesClick: (MediaItemEntity) -> Unit,
    onFavoriteToggle: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (series.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nenhuma série encontrada na sua biblioteca.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 130.dp),
            modifier = modifier
                .fillMaxSize()
                .testTag("series_grid"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(series, key = { it.id }) { item ->
                MediaCard(
                    media = item,
                    onClick = { onSeriesClick(item) },
                    onFavoriteToggle = { onFavoriteToggle(item) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
