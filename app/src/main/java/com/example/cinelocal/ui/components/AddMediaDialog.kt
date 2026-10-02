package com.example.cinelocal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cinelocal.data.torrent.TorrentUtils
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkBackground
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary

@Composable
fun AddMediaDialog(
    onDismiss: () -> Unit,
    onPickFolderClick: () -> Unit,
    onAddDirectStream: (title: String, url: String, isSeries: Boolean) -> Unit,
    onAddTorrentStream: (magnetUri: String, customTitle: String, isSeries: Boolean) -> Unit = { _, _, _ -> }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    // Direct stream
    var streamTitle by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var isSeries by remember { mutableStateOf(false) }

    // Magnet Torrent
    var magnetUri by remember { mutableStateOf("") }
    var magnetTitle by remember { mutableStateOf("") }
    var isTorrentSeries by remember { mutableStateOf(false) }
    var showMagnetGuide by remember { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                text = "Adicionar à Biblioteca",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
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
                        text = { Text("Pasta Local", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        selectedContentColor = CineRed,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Link / Stream", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        selectedContentColor = CineRed,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Torrent Magnet", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        selectedContentColor = CineRed,
                        unselectedContentColor = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    0 -> {
                        Text(
                            text = "Selecione uma pasta com arquivos de vídeo (.mp4, .mkv, .avi) do seu armazenamento local, pendrive ou cartão SD.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onDismiss()
                                    onPickFolderClick()
                                }
                                .testTag("pick_folder_action"),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = CineRed,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Abrir Explorador de Arquivos",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    1 -> {
                        OutlinedTextField(
                            value = streamTitle,
                            onValueChange = { streamTitle = it },
                            label = { Text("Título da Mídia") },
                            placeholder = { Text("Ex: Interestelar ou The Last of Us") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CineRed,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = streamUrl,
                            onValueChange = { streamUrl = it },
                            label = { Text("URL do Vídeo / Stream (.mp4, .m3u8)") },
                            placeholder = { Text("https://servidor.com/video.mp4") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CineRed,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { isSeries = false }
                            ) {
                                RadioButton(
                                    selected = !isSeries,
                                    onClick = { isSeries = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = CineRed)
                                )
                                Text("Filme", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { isSeries = true }
                            ) {
                                RadioButton(
                                    selected = isSeries,
                                    onClick = { isSeries = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = CineRed)
                                )
                                Text("Série", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    2 -> {
                        // Magnet Torrent Tab
                        OutlinedTextField(
                            value = magnetUri,
                            onValueChange = { input ->
                                magnetUri = input
                                if (magnetUri.contains("magnet:", ignoreCase = true) && magnetTitle.isBlank()) {
                                    val parsed = TorrentUtils.parseMagnet(magnetUri)
                                    if (parsed.name.isNotBlank()) {
                                        magnetTitle = parsed.name
                                    }
                                }
                            },
                            label = { Text("Link Magnet (magnet:?xt=urn:btih...)") },
                            placeholder = { Text("magnet:?xt=urn:btih:08ada5a7...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("magnet_link_input"),
                            maxLines = 3,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CineRed,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.getText()?.text?.let { clipText ->
                                        if (clipText.isNotBlank()) {
                                            magnetUri = clipText
                                            val parsed = TorrentUtils.parseMagnet(clipText)
                                            if (parsed.name.isNotBlank()) {
                                                magnetTitle = parsed.name
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("paste_magnet_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Colar Link", fontSize = 12.sp)
                            }

                            TextButton(onClick = { showMagnetGuide = !showMagnetGuide }) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = null,
                                    tint = CineRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showMagnetGuide) "Ocultar Guia" else "Como Funciona?",
                                    color = CineRed,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        AnimatedVisibility(visible = showMagnetGuide) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = " Como usar Magnet Links:",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = CineRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "1. Copie o link 'Magnet' do filme/série em qualquer site de torrent (YTS, 1337x, PirateBay, etc).\n" +
                                                "2. Clique no botão 'Colar Link' acima.\n" +
                                                "3. Clique em 'Adicionar Magnet' para reproduzir e assistir diretamente no player do CineLocal!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = magnetTitle,
                            onValueChange = { magnetTitle = it },
                            label = { Text("Título Personalizado (Opcional)") },
                            placeholder = { Text("Ex: Matrix 1080p Dual Áudio") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CineRed,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { isTorrentSeries = false }
                            ) {
                                RadioButton(
                                    selected = !isTorrentSeries,
                                    onClick = { isTorrentSeries = false },
                                    colors = RadioButtonDefaults.colors(selectedColor = CineRed)
                                )
                                Text("Filme", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { isTorrentSeries = true }
                            ) {
                                RadioButton(
                                    selected = isTorrentSeries,
                                    onClick = { isTorrentSeries = true },
                                    colors = RadioButtonDefaults.colors(selectedColor = CineRed)
                                )
                                Text("Série", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 1) {
                Button(
                    onClick = {
                        if (streamTitle.isNotBlank() && streamUrl.isNotBlank()) {
                            onAddDirectStream(streamTitle, streamUrl, isSeries)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                    enabled = streamTitle.isNotBlank() && streamUrl.isNotBlank()
                ) {
                    Text("Adicionar Mídia", color = Color.White)
                }
            } else if (selectedTab == 2) {
                Button(
                    onClick = {
                        if (magnetUri.isNotBlank()) {
                            onAddTorrentStream(magnetUri, magnetTitle, isTorrentSeries)
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                    enabled = magnetUri.isNotBlank(),
                    modifier = Modifier.testTag("confirm_add_torrent_button")
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Adicionar Magnet", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextSecondary)
            }
        }
    )
}
