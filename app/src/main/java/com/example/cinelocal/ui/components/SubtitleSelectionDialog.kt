package com.example.cinelocal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cinelocal.data.model.TrackInfo
import com.example.cinelocal.data.subtitles.OpenSubtitlesClient
import com.example.cinelocal.data.subtitles.SubtitleItem
import com.example.cinelocal.ui.theme.AccentGold
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkBackground
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SubtitleSelectionDialog(
    mediaTitle: String,
    openSubtitlesApiKey: String,
    availableSubtitleTracks: List<TrackInfo>,
    selectedSubtitleIndex: Int,
    activeExternalSubtitleLabel: String? = null,
    onSelectEmbeddedTrack: (Int) -> Unit,
    onApplyExternalSubtitle: (file: File, vttContent: String, label: String) -> Unit,
    onDisableSubtitles: () -> Unit,
    onOpenSettingsForApiKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { OpenSubtitlesClient(context) }

    var selectedTab by remember { mutableIntStateOf(if (availableSubtitleTracks.isNotEmpty()) 0 else 1) }
    var searchQuery by remember {
        val clean = mediaTitle.replace(Regex("""(?i)\b(1080p|720p|2160p|4k|bluray|web-dl|x264|x265|hevc|dual|dublado)\b"""), "").trim()
        mutableStateOf(clean)
    }

    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var downloadingFileId by remember { mutableStateOf<Long?>(null) }

    fun doSearch() {
        if (openSubtitlesApiKey.isBlank()) {
            searchError = "Chave de API do OpenSubtitles não configurada."
            return
        }
        if (searchQuery.isBlank()) return

        scope.launch {
            isSearching = true
            searchError = null
            try {
                val results = client.searchSubtitles(
                    apiKey = openSubtitlesApiKey,
                    query = searchQuery
                )
                searchResults = results
                if (results.isEmpty()) {
                    searchError = "Nenhuma legenda encontrada para '$searchQuery'."
                }
            } catch (e: Exception) {
                searchError = "Erro na busca: ${e.localizedMessage}"
            } finally {
                isSearching = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (openSubtitlesApiKey.isNotBlank() && searchQuery.isNotBlank()) {
            doSearch()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Subtitles, contentDescription = null, tint = CineRed)
                Text(
                    text = "Legendas & Idiomas",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = DarkSurfaceVariant,
                    contentColor = TextPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = CineRed
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Faixas do Vídeo (${availableSubtitleTracks.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        selectedContentColor = CineRed,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("OpenSubtitles", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        selectedContentColor = CineRed,
                        unselectedContentColor = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        // Embedded Tracks & Option to Turn Off
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Desativar Legendas
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onDisableSubtitles()
                                            onDismiss()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedSubtitleIndex == -1 && activeExternalSubtitleLabel == null) CineRed.copy(alpha = 0.2f) else DarkSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.SubtitlesOff, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Desativada",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        if (selectedSubtitleIndex == -1 && activeExternalSubtitleLabel == null) {
                                            Icon(Icons.Default.Check, contentDescription = "Selecionada", tint = CineRed, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            // External Subtitle Active
                            if (activeExternalSubtitleLabel != null) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = CineRed.copy(alpha = 0.2f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentGold, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Text(
                                                        text = "Legenda Baixada (Ativa)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = AccentGold,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = activeExternalSubtitleLabel,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = TextPrimary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            Icon(Icons.Default.Check, contentDescription = null, tint = CineRed, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            // Embedded Tracks
                            items(availableSubtitleTracks) { track ->
                                val isSelected = selectedSubtitleIndex == track.index && activeExternalSubtitleLabel == null
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectEmbeddedTrack(track.index)
                                            onDismiss()
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) CineRed.copy(alpha = 0.2f) else DarkSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (!track.language.isNullOrBlank()) {
                                                Text(
                                                    text = "Idioma: ${track.language}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "Selecionada", tint = CineRed, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            if (availableSubtitleTracks.isEmpty() && activeExternalSubtitleLabel == null) {
                                item {
                                    Text(
                                        text = "Nenhuma faixa de legenda embutida no arquivo. Use a aba OpenSubtitles para buscar legendas online.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // OpenSubtitles.com Tab
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (openSubtitlesApiKey.isBlank()) {
                                Surface(
                                    color = DarkBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Key, contentDescription = null, tint = AccentGold, modifier = Modifier.size(28.dp))
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Chave da API Necessária",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Cadastre gratuitamente sua API Key do OpenSubtitles.com para buscar e sincronizar legendas em português.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                onDismiss()
                                                onOpenSettingsForApiKey()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Configurar API Key", fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                // Search Bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Nome do filme ou série…", fontSize = 12.sp) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CineRed,
                                            unfocusedBorderColor = DarkSurfaceVariant,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { doSearch() },
                                        enabled = !isSearching,
                                        colors = ButtonDefaults.buttonColors(containerColor = CineRed)
                                    ) {
                                        if (isSearching) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Search, contentDescription = "Buscar")
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (searchError != null) {
                                    Text(
                                        text = searchError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CineRed,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 240.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(searchResults) { sub ->
                                        val isDownloading = downloadingFileId == sub.fileId
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Surface(
                                                            color = CineRed.copy(alpha = 0.2f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = sub.language.uppercase(),
                                                                color = CineRed,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 9.sp,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        if (sub.hearingImpaired) {
                                                            Icon(
                                                                Icons.Default.HearingDisabled,
                                                                contentDescription = "Acessibilidade",
                                                                tint = AccentGold,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                        if (sub.rating > 0) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Default.Star, contentDescription = null, tint = AccentGold, modifier = Modifier.size(12.dp))
                                                                Text(
                                                                    text = String.format("%.1f", sub.rating),
                                                                    fontSize = 10.sp,
                                                                    color = TextSecondary
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "• ${sub.downloadCount} downloads",
                                                            fontSize = 10.sp,
                                                            color = TextSecondary
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = sub.releaseName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextPrimary,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                OutlinedButton(
                                                    onClick = {
                                                        scope.launch {
                                                            downloadingFileId = sub.fileId
                                                            val downloaded = client.downloadAndConvertSubtitle(
                                                                apiKey = openSubtitlesApiKey,
                                                                fileId = sub.fileId,
                                                                fileName = sub.fileName
                                                            )
                                                            downloadingFileId = null
                                                            if (downloaded != null) {
                                                                onApplyExternalSubtitle(
                                                                    downloaded.first,
                                                                    downloaded.second,
                                                                    sub.releaseName
                                                                )
                                                                onDismiss()
                                                            }
                                                        }
                                                    },
                                                    enabled = !isDownloading,
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    if (isDownloading) {
                                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = CineRed)
                                                    } else {
                                                        Text("Baixar", fontSize = 11.sp, color = TextPrimary)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color.White)
            }
        }
    )
}
