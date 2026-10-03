package com.example.cinelocal.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cinelocal.data.model.SubtitleFileEntity
import com.example.cinelocal.data.model.TrackInfo
import com.example.cinelocal.data.subtitles.OpenSubtitlesClient
import com.example.cinelocal.data.subtitles.OsResult
import com.example.cinelocal.data.subtitles.SubtitleItem
import com.example.cinelocal.ui.theme.AccentGold
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkBackground
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun SubtitleSelectionDialog(
    mediaTitle: String,
    openSubtitlesApiKey: String,
    openSubtitlesUsername: String = "",
    openSubtitlesPassword: String = "",
    episodeId: String? = null,
    videoUri: Uri? = null,
    downloadedSubtitles: List<SubtitleFileEntity> = emptyList(),
    availableSubtitleTracks: List<TrackInfo>,
    selectedSubtitleIndex: Int,
    activeExternalSubtitleLabel: String? = null,
    onSelectEmbeddedTrack: (Int) -> Unit,
    onSelectDownloadedSubtitle: (SubtitleFileEntity) -> Unit,
    onDownloadAndApplySubtitle: (
        fileId: Long,
        fileName: String,
        language: String,
        releaseName: String,
        onResult: (OsResult<SubtitleFileEntity>) -> Unit
    ) -> Unit,
    onDisableSubtitles: () -> Unit,
    onOpenSettingsForApiKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { OpenSubtitlesClient(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember {
        val clean = mediaTitle.replace(Regex("""(?i)\b(1080p|720p|2160p|4k|bluray|web-dl|x264|x265|hevc|dual|dublado)\b"""), "").trim()
        mutableStateOf(clean)
    }

    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<SubtitleItem>>(emptyList()) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var downloadingFileId by remember { mutableStateOf<Long?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    fun doSearch() {
        if (openSubtitlesApiKey.isBlank()) {
            searchError = "Chave de API do OpenSubtitles não configurada."
            return
        }

        scope.launch {
            isSearching = true
            searchError = null
            downloadError = null
            val result = client.searchSubtitles(
                apiKey = openSubtitlesApiKey,
                username = openSubtitlesUsername,
                password = openSubtitlesPassword,
                query = searchQuery,
                videoUri = videoUri
            )
            isSearching = false
            when (result) {
                is OsResult.Success -> {
                    searchResults = result.data
                    if (result.data.isEmpty()) {
                        searchError = "Nenhuma legenda encontrada para '$searchQuery'."
                    }
                }
                is OsResult.Error -> {
                    searchError = result.message
                }
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
                        text = {
                            val totalLocal = downloadedSubtitles.size + availableSubtitleTracks.size
                            Text("Legendas ($totalLocal)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        },
                        selectedContentColor = CineRed,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("OpenSubtitles Online", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        selectedContentColor = CineRed,
                        unselectedContentColor = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTab) {
                    0 -> {
                        // Local / Downloaded / Embedded Subtitles
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Option: Turn Off Subtitles
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

                            // Downloaded / External Subtitles Section from DB
                            if (downloadedSubtitles.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Legendas Baixadas / Salvas (${downloadedSubtitles.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentGold,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }

                                items(downloadedSubtitles) { sub ->
                                    val isSelected = activeExternalSubtitleLabel == sub.label
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onSelectDownloadedSubtitle(sub)
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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = AccentGold, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                                        Text(
                                                            text = sub.label,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = TextPrimary,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, contentDescription = "Selecionada", tint = CineRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Embedded Tracks Section
                            if (availableSubtitleTracks.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Faixas do Arquivo de Vídeo (${availableSubtitleTracks.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }

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
                            }

                            if (downloadedSubtitles.isEmpty() && availableSubtitleTracks.isEmpty() && activeExternalSubtitleLabel == null) {
                                item {
                                    Text(
                                        text = "Nenhuma legenda local para este vídeo. Use a aba OpenSubtitles Online para buscar e baixar.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(vertical = 12.dp)
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // OpenSubtitles Online Tab
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
                                            text = "Configuração do OpenSubtitles.com",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Cadastre gratuitamente sua API Key e seu usuário/senha do OpenSubtitles.com para buscar e sincronizar legendas em português.",
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
                                            Text("Configurar Credenciais", fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                // Search Input
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
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Error, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = searchError!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                if (downloadError != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Default.Error, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = downloadError!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
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
                                                        if (sub.isHashMatch) {
                                                            Surface(
                                                                color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = "HASH OK",
                                                                    color = Color(0xFF4CAF50),
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 9.sp,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                            }
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
                                                            text = "• ${sub.downloadCount} dl",
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
                                                        downloadingFileId = sub.fileId
                                                        downloadError = null
                                                        onDownloadAndApplySubtitle(
                                                            sub.fileId,
                                                            sub.fileName,
                                                            sub.language,
                                                            sub.releaseName
                                                        ) { result ->
                                                            downloadingFileId = null
                                                            when (result) {
                                                                is OsResult.Success -> {
                                                                    onDismiss()
                                                                }
                                                                is OsResult.Error -> {
                                                                    downloadError = result.message
                                                                }
                                                            }
                                                        }
                                                    },
                                                    enabled = downloadingFileId == null,
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
