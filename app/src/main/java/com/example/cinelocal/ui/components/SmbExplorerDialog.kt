package com.example.cinelocal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.cinelocal.data.model.NetworkServerEntity
import com.example.cinelocal.data.scanner.MediaNameParser
import com.example.cinelocal.data.scanner.SmbFolderScanner
import com.example.cinelocal.data.smb.DiscoveredPc
import com.example.cinelocal.data.smb.NetworkDiscovery
import com.example.cinelocal.data.smb.SmbClientManager
import com.example.cinelocal.data.smb.SmbConnectionConfig
import com.example.cinelocal.data.smb.SmbFileItem
import com.example.cinelocal.data.smb.SmbShareItem
import com.example.cinelocal.data.smb.SmbStreamProxy
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkBackground
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmbExplorerDialog(
    onDismiss: () -> Unit,
    savedServers: List<NetworkServerEntity>,
    onSaveServer: (NetworkServerEntity) -> Unit,
    onDeleteServer: (NetworkServerEntity) -> Unit,
    onPlaySmbVideo: (streamUrl: String, title: String) -> Unit,
    onImportFolderToLibrary: (config: SmbConnectionConfig, shareName: String, dirPath: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estados gerais
    var showGuideDialog by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }

    // Estado da descoberta de rede
    var isScanningNetwork by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var discoveredPcs by remember { mutableStateOf<List<DiscoveredPc>>(emptyList()) }

    // Estado do servidor ativo
    var activeServer by remember { mutableStateOf<NetworkServerEntity?>(null) }
    var activeConfig by remember { mutableStateOf<SmbConnectionConfig?>(null) }
    var currentShare by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf("") }

    // Estados do navegador de pastas
    var sharesList by remember { mutableStateOf<List<SmbShareItem>>(emptyList()) }
    var filesList by remember { mutableStateOf<List<SmbFileItem>>(emptyList()) }
    var isLoadingContent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Estado de catalogação em andamento
    var isCataloging by remember { mutableStateOf(false) }
    var catalogProgressText by remember { mutableStateOf("") }

    // Função para carregar conteúdo da pasta atual
    fun loadDirectory(config: SmbConnectionConfig, share: String, path: String) {
        isLoadingContent = true
        errorMessage = null
        scope.launch {
            try {
                val list = SmbClientManager.listDirectory(config, share, path)
                filesList = list
                currentShare = share
                currentPath = path
            } catch (e: Exception) {
                errorMessage = "Erro ao acessar pasta: ${e.message}"
            } finally {
                isLoadingContent = false
            }
        }
    }

    // Função para carregar shares do servidor
    fun loadShares(config: SmbConnectionConfig) {
        isLoadingContent = true
        errorMessage = null
        scope.launch {
            try {
                val shares = SmbClientManager.listShares(config)
                sharesList = shares
                currentShare = null
                currentPath = ""
            } catch (e: Exception) {
                errorMessage = "Não foi possível listar os compartilhamentos: ${e.message}"
            } finally {
                isLoadingContent = false
            }
        }
    }

    // Função para iniciar escaneamento de rede local
    fun startNetworkScan() {
        if (isScanningNetwork) return
        isScanningNetwork = true
        discoveredPcs = emptyList()
        scope.launch {
            try {
                val pcs = NetworkDiscovery.discoverLocalPcs(context) { scanned, total, found ->
                    scanProgress = scanned.toFloat() / total.toFloat()
                    if (found != null) {
                        discoveredPcs = (discoveredPcs + found).distinctBy { it.ip }
                    }
                }
                discoveredPcs = pcs
            } catch (e: Exception) {
                errorMessage = "Erro na busca de rede: ${e.message}"
            } finally {
                isScanningNetwork = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            color = DarkBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // TopBar do Explorador
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeServer != null) {
                            IconButton(
                                onClick = {
                                    if (currentPath.isNotBlank()) {
                                        // Voltar um nível de pasta
                                        val parent = currentPath.substringBeforeLast('/', "")
                                        loadDirectory(activeConfig!!, currentShare!!, parent)
                                    } else if (currentShare != null) {
                                        // Voltar para lista de shares
                                        loadShares(activeConfig!!)
                                    } else {
                                        // Voltar para lista de computadores
                                        activeServer = null
                                        activeConfig = null
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Voltar",
                                    tint = TextPrimary
                                )
                            }
                        } else {
                            Surface(
                                color = CineRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Computer,
                                        contentDescription = null,
                                        tint = CineRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column {
                            Text(
                                text = if (activeServer != null) {
                                    if (currentShare != null) "${activeServer!!.name} > $currentShare" else activeServer!!.name
                                } else "Armazenamento no PC (Rede SMB)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (activeServer != null) {
                                    if (currentPath.isNotBlank()) "/$currentPath" else "Compartilhamentos disponíveis"
                                } else "Acesse pastas e vídeos do computador pela Wi-Fi",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showGuideDialog = true },
                            modifier = Modifier.testTag("open_pc_guide_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Como preparar o PC",
                                tint = Color(0xFF38BDF8)
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = TextSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = DarkSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                // Feedback de erro ou sucesso
                if (errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { errorMessage = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                if (successMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF14532D)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = successMessage!!,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Corpo do diálogo: Se não tiver servidor ativo -> Tela de seleção/descoberta de PCs
                if (activeServer == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        // Ações de conexão
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { startNetworkScan() },
                                colors = ButtonDefaults.buttonColors(containerColor = if (isScanningNetwork) DarkSurfaceVariant else CineRed),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("scan_network_button"),
                                enabled = !isScanningNetwork
                            ) {
                                if (isScanningNetwork) {
                                    CircularProgressIndicator(
                                        color = CineRed,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buscando...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Buscar PCs Wi-Fi", fontSize = 12.sp, color = Color.White)
                                }
                            }

                            OutlinedButton(
                                onClick = { showManualAddDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("manual_add_pc_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Conectar por IP", fontSize = 12.sp, color = TextPrimary)
                            }
                        }

                        if (isScanningNetwork) {
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { scanProgress },
                                color = CineRed,
                                trackColor = DarkSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Computadores descobertos na rede
                            if (discoveredPcs.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "COMPUTADORES ENCONTRADOS NA REDE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF38BDF8),
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                }

                                items(discoveredPcs) { pc ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val config = SmbConnectionConfig(host = pc.ip, isAnonymous = true)
                                                val server = NetworkServerEntity(
                                                    name = pc.hostName,
                                                    host = pc.ip,
                                                    isAnonymous = true
                                                )
                                                onSaveServer(server)
                                                activeServer = server
                                                activeConfig = config
                                                loadShares(config)
                                            },
                                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = Color(0xFF0284C7).copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Computer,
                                                            contentDescription = null,
                                                            tint = Color(0xFF38BDF8),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = pc.hostName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = TextPrimary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "IP: ${pc.ip} • Porta 445 (SMB)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val config = SmbConnectionConfig(host = pc.ip, isAnonymous = true)
                                                    val server = NetworkServerEntity(
                                                        name = pc.hostName,
                                                        host = pc.ip,
                                                        isAnonymous = true
                                                    )
                                                    onSaveServer(server)
                                                    activeServer = server
                                                    activeConfig = config
                                                    loadShares(config)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Acessar", fontSize = 12.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }

                            // Computadores salvos
                            item {
                                Text(
                                    text = "MEUS COMPUTADORES SALVOS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }

                            if (savedServers.isEmpty()) {
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Storage,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Nenhum computador salvo",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "Clique em 'Buscar PCs Wi-Fi' ou 'Conectar por IP' para começar.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(savedServers) { server ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val config = SmbConnectionConfig(
                                                    host = server.host,
                                                    port = server.port,
                                                    username = server.username,
                                                    password = server.password,
                                                    domain = server.domain,
                                                    isAnonymous = server.isAnonymous
                                                )
                                                activeServer = server
                                                activeConfig = config
                                                loadShares(config)
                                            },
                                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = CineRed.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.size(40.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = Icons.Default.Computer,
                                                            contentDescription = null,
                                                            tint = CineRed,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        text = server.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = TextPrimary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${server.host} • ${if (server.isAnonymous) "Acesso Anônimo" else "Usuário: ${server.username}"}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { onDeleteServer(server) }) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Excluir",
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        val config = SmbConnectionConfig(
                                                            host = server.host,
                                                            port = server.port,
                                                            username = server.username,
                                                            password = server.password,
                                                            domain = server.domain,
                                                            isAnonymous = server.isAnonymous
                                                        )
                                                        activeServer = server
                                                        activeConfig = config
                                                        loadShares(config)
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                        contentDescription = "Abrir",
                                                        tint = CineRed
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Servidor ativo: Navegador de Compartilhamentos e Pastas
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        // Se estiver dentro de um compartilhamento, exibir botão de catalogar pasta
                        if (currentShare != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        onImportFolderToLibrary(activeConfig!!, currentShare!!, currentPath)
                                        successMessage = "Catalogação da pasta iniciada na biblioteca!"
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    modifier = Modifier.testTag("catalog_current_folder_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Catalogar Esta Pasta no App",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        loadDirectory(activeConfig!!, currentShare!!, currentPath)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Atualizar",
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }

                        if (isLoadingContent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = CineRed)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Carregando arquivos do computador...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        } else if (currentShare == null) {
                            // Lista de Compartilhamentos (Shares)
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Text(
                                        text = "PASTAS COMPARTILHADAS (SHARES)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                items(sharesList) { share ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                loadDirectory(activeConfig!!, share.name, "")
                                            },
                                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOpen,
                                                contentDescription = null,
                                                tint = Color(0xFF38BDF8),
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = share.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = TextPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (!share.comment.isNullOrBlank()) {
                                                    Text(
                                                        text = share.comment,
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
                        } else {
                            // Lista de Arquivos e Pastas dentro do Share
                            if (filesList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Nenhum arquivo ou subpasta nesta pasta.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(filesList) { item ->
                                        if (item.isDirectory) {
                                            // Item Diretório
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        loadDirectory(activeConfig!!, item.shareName, item.path)
                                                    },
                                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFBBF24),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text(
                                                        text = item.name,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = TextPrimary,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                                        contentDescription = null,
                                                        tint = TextSecondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            // Item Arquivo de Vídeo ou Outro
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.8f)),
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
                                                        modifier = Modifier.weight(1f),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = if (item.isVideo) Icons.Default.Movie else Icons.Default.Storage,
                                                            contentDescription = null,
                                                            tint = if (item.isVideo) CineRed else TextSecondary,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text(
                                                                text = item.name,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Medium,
                                                                color = TextPrimary,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = formatBytes(item.sizeBytes),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = TextSecondary,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    if (item.isVideo) {
                                                        Button(
                                                            onClick = {
                                                                val streamUrl = SmbStreamProxy.createProxyUrl(
                                                                    activeConfig!!,
                                                                    item.shareName,
                                                                    item.path
                                                                )
                                                                onDismiss()
                                                                onPlaySmbVideo(streamUrl, item.name)
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                                                            shape = RoundedCornerShape(6.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.PlayArrow,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Assistir", fontSize = 11.sp, color = Color.White)
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
            }
        }
    }

    // Modal de Cadastro Manual de Computador
    if (showManualAddDialog) {
        var hostInput by remember { mutableStateOf("") }
        var nameInput by remember { mutableStateOf("") }
        var isAnonymous by remember { mutableStateOf(true) }
        var userInput by remember { mutableStateOf("") }
        var passwordInput by remember { mutableStateOf("") }
        var domainInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualAddDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "Conectar Computador (IP)",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("Endereço IP ou Nome do Host") },
                        placeholder = { Text("Ex: 192.168.1.100 ou MEU-PC") },
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

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Nome Amigável (Opcional)") },
                        placeholder = { Text("Ex: Computador Sala") },
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Acesso Anônimo / Convidado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Switch(
                            checked = isAnonymous,
                            onCheckedChange = { isAnonymous = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = CineRed, checkedTrackColor = CineRed.copy(alpha = 0.5f))
                        )
                    }

                    if (!isAnonymous) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { userInput = it },
                            label = { Text("Usuário do Windows") },
                            placeholder = { Text("Ex: andre ou andre@email.com") },
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

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Senha do Windows") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CineRed,
                                unfocusedBorderColor = DarkSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (hostInput.isNotBlank()) {
                            val server = NetworkServerEntity(
                                name = nameInput.ifBlank { "PC ($hostInput)" },
                                host = hostInput.trim(),
                                username = userInput.trim(),
                                password = passwordInput,
                                domain = domainInput.trim(),
                                isAnonymous = isAnonymous
                            )
                            onSaveServer(server)
                            showManualAddDialog = false
                            val config = SmbConnectionConfig(
                                host = server.host,
                                port = server.port,
                                username = server.username,
                                password = server.password,
                                domain = server.domain,
                                isAnonymous = server.isAnonymous
                            )
                            activeServer = server
                            activeConfig = config
                            loadShares(config)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                    enabled = hostInput.isNotBlank()
                ) {
                    Text("Salvar e Conectar", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualAddDialog = false }) {
                    Text("Cancelar", color = TextSecondary)
                }
            }
        )
    }

    // Modal do Guia de Preparação do PC
    if (showGuideDialog) {
        PcSetupGuideDialog(
            onDismiss = { showGuideDialog = false },
            onReadyToConnect = {
                showGuideDialog = false
                startNetworkScan()
            }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
