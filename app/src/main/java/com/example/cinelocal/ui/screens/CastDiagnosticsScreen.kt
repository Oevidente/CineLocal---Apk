package com.example.cinelocal.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cinelocal.cast.CastManager
import com.example.cinelocal.ui.theme.AccentGold
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkBackground
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDiagnosticsScreen(
    castManager: CastManager,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var testStatus by remember { mutableStateOf<String?>(null) }

    // Informações de diagnóstico
    val gmsStatus = remember {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    }
    val isGmsAvailable = gmsStatus == ConnectionResult.SUCCESS
    val ipAddress = remember { castManager.getDeviceIpAddress() }
    val isServerRunning = remember { castManager.isProxyRunning() }
    val serverPort = remember { castManager.getProxyPort() }
    val castState = castManager.castState.value

    fun generateReport(): String {
        return buildString {
            appendLine("=== Relatório de Diagnóstico Google Cast ===")
            appendLine("Google Play Services: ${if (isGmsAvailable) "Disponível" else "Indisponível (Código: $gmsStatus)"}")
            appendLine("IP Local da Rede: $ipAddress")
            appendLine("Servidor Proxy: ${if (isServerRunning) "Ativo na porta $serverPort" else "Inativo"}")
            appendLine("Sessão Cast Conectada: ${castState.isConnected}")
            appendLine("Aparelho Cast: ${castState.deviceName ?: "Nenhum"}")
            appendLine("Dispositivos Encontrados: ${castState.availableDevices.size}")
            appendLine("--- Últimos Logs do Proxy ---")
            castManager.getProxyLogs().forEach { appendLine(it) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Diagnóstico do Cast", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Cards
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Conectividade e Ambiente", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

                    DiagItem(
                        title = "Google Play Services",
                        value = if (isGmsAvailable) "Disponível (OK)" else "Incompatível/Ausente",
                        isOk = isGmsAvailable
                    )
                    DiagItem(
                        title = "IP da Rede Wi-Fi",
                        value = ipAddress,
                        isOk = ipAddress != "127.0.0.1"
                    )
                    DiagItem(
                        title = "Servidor Proxy Local",
                        value = if (isServerRunning) "Ativo (Porta $serverPort)" else "Inativo (Inicia ao transmitir)",
                        isOk = true
                    )
                    DiagItem(
                        title = "Sessão Chromecast",
                        value = if (castState.isConnected) "Conectado a ${castState.deviceName}" else "Desconectado",
                        isOk = castState.isConnected
                    )
                }
            }

            // Ações de Teste
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Testes de Transmissão", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Teste a conexão com a TV enviando um stream público direto para o Chromecast.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)

                    Button(
                        onClick = {
                            if (!castState.isConnected) {
                                testStatus = "Conecte-se a uma TV primeiro pelo botão Cast."
                                Toast.makeText(context, testStatus, Toast.LENGTH_SHORT).show()
                            } else {
                                castManager.castPublicTestVideo()
                                testStatus = "Vídeo público de teste enviado para a TV!"
                                Toast.makeText(context, testStatus, Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testar com Vídeo Público (Big Buck Bunny)")
                    }

                    testStatus?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = AccentGold)
                    }
                }
            }

            // Logs do Servidor
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Últimos Registros do Proxy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }

                    val logs = castManager.getProxyLogs()
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (logs.isEmpty()) {
                                Text("Nenhuma requisição registrada até o momento.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            } else {
                                logs.forEach { logLine ->
                                    Text(
                                        text = logLine,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Botão Copiar Relatório
            OutlinedButton(
                onClick = {
                    val report = generateReport()
                    clipboardManager.setText(AnnotatedString(report))
                    Toast.makeText(context, "Relatório copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copiar Relatório Completo", color = TextPrimary)
            }
        }
    }
}

@Composable
private fun DiagItem(title: String, value: String, isOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isOk) Color(0xFF22C55E) else CineRed,
                modifier = Modifier.size(16.dp)
            )
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}
