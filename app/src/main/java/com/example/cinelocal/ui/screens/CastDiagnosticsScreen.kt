package com.example.cinelocal.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cinelocal.cast.CastManager
import com.example.cinelocal.data.torrent.LocalNetworkUtils
import com.example.cinelocal.ui.theme.AccentGold
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkBackground
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CastDiagnosticsScreen(
    castManager: CastManager,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var isRunning by remember { mutableStateOf(castManager.isProxyRunning()) }
    var currentIp by remember { mutableStateOf(castManager.getDeviceIpAddress()) }
    var currentPort by remember { mutableStateOf(castManager.getProxyPort()) }
    val logs = remember { mutableStateListOf<String>() }
    var networkInterfaces by remember { mutableStateOf<List<String>>(emptyList()) }

    var isPingTesting by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Periodic polling to update live logs and server state
    LaunchedEffect(Unit) {
        networkInterfaces = LocalNetworkUtils.getActiveInterfacesInfo()
        while (true) {
            isRunning = castManager.isProxyRunning()
            currentIp = castManager.getDeviceIpAddress()
            currentPort = castManager.getProxyPort()

            val latestLogs = castManager.getProxyLogs()
            logs.clear()
            logs.addAll(latestLogs)

            if (logs.isNotEmpty()) {
                listState.animateScrollToItem(logs.size - 1)
            }
            delay(1000)
        }
    }

    fun copyLogsToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("CineLocal Cast Logs", logs.joinToString("\n"))
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Logs copiados para a área de transferência!", Toast.LENGTH_SHORT).show()
    }

    fun runPingTest() {
        scope.launch {
            isPingTesting = true
            pingResult = null
            pingResult = castManager.proxyServer.pingSelf()
            isPingTesting = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cast_diagnostics_screen"),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Diagnóstico do Cast & Servidor",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Modo Desenvolvedor • Status e Logs em Tempo Real",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Server Status Card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(if (isRunning) Color(0xFF4CAF50) else CineRed, CircleShape)
                            )
                            Text(
                                text = if (isRunning) "SERVIDOR ATIVO (RUNNING)" else "SERVIDOR PARADO",
                                fontWeight = FontWeight.Bold,
                                color = if (isRunning) Color(0xFF4CAF50) else CineRed,
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "http://$currentIp:$currentPort",
                            fontFamily = FontFamily.Monospace,
                            color = AccentGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (networkInterfaces.isNotEmpty()) {
                        Text(
                            text = "Interfaces de Rede Ativas:",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        networkInterfaces.forEach { netInfo ->
                            Text(
                                text = "• $netInfo",
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { runPingTest() },
                            enabled = !isPingTesting,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isPingTesting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = CineRed, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = CineRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text("Ping", fontSize = 11.sp, color = TextPrimary)
                        }

                        Button(
                            onClick = { castManager.restartCastServiceAndDiscovery() },
                            colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reiniciar Cast", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    pingResult?.let { res ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (res.first) Color(0xFF1B5E20) else Color(0xFFB71C1C)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (res.first) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = res.second,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Real-Time Terminal Log Feed
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, DarkSurfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Router, contentDescription = null, tint = AccentGold, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Logs do Servidor & Cast (${logs.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentGold,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row {
                            IconButton(onClick = { copyLogsToClipboard() }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { castManager.proxyServer.clearLogs() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Limpar", tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (logs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum log gravado ainda.\nTente reproduzir ou transmitir uma mídia para ver as requisições em tempo real.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(logs) { logLine ->
                                val color = when {
                                    logLine.contains("ERRO", ignoreCase = true) || logLine.contains("FALHA", ignoreCase = true) -> CineRed
                                    logLine.contains("SUCESSO", ignoreCase = true) || logLine.contains("PONG", ignoreCase = true) -> Color(0xFF4CAF50)
                                    logLine.contains("Registrada", ignoreCase = true) || logLine.contains("Transmitindo", ignoreCase = true) -> AccentGold
                                    else -> TextPrimary
                                }
                                Text(
                                    text = logLine,
                                    color = color,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
