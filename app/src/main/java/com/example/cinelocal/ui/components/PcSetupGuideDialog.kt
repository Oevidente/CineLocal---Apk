package com.example.cinelocal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary

@Composable
fun PcSetupGuideDialog(
    onDismiss: () -> Unit,
    onReadyToConnect: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        modifier = Modifier.testTag("pc_setup_guide_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = CineRed.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = CineRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Como Preparar o PC (Windows)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Passo a passo para liberar pastas e evitar lista vazia",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Aviso de mesma rede Wi-Fi
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Importante: Celular e computador devem estar conectados no mesmo roteador / Wi-Fi!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Passo 1: Descoberta de Rede
                StepItemCard(
                    stepNumber = "1",
                    icon = Icons.Default.Router,
                    title = "Ativar Descoberta de Rede no Windows",
                    description = "1. Abra Configurações do Windows > Rede e Internet > Configurações avançadas de rede > Compartilhamento avançado.\n" +
                            "2. Marque a sua rede como 'Rede Privada'.\n" +
                            "3. Ative 'Descoberta de rede' e 'Compartilhamento de arquivos e impressoras'.\n" +
                            "4. Em 'Todas as redes', se desejar acesso sem senha, marque 'Desativar compartilhamento protegido por senha'."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Passo 2: Compartilhar Pasta
                StepItemCard(
                    stepNumber = "2",
                    icon = Icons.Default.FolderShared,
                    title = "Aba Compartilhamento (Nome da Pasta)",
                    description = "1. Clique com o botão direito na pasta de vídeos (ex: C:\\Filmes) > Propriedades > Aba Compartilhamento.\n" +
                            "2. Clique em 'Compartilhamento Avançado...'.\n" +
                            "3. Marque 'Compartilhar esta pasta' e anote o nome do compartilhamento (ex: Filmes).\n" +
                            "4. Clique em 'Permissões' > Verifique se 'Todos' está adicionado com permissão de 'Leitura' marcada."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Passo 3: Segurança NTFS (Crítico no Windows 10/11)
                StepItemCard(
                    stepNumber = "3",
                    icon = Icons.Default.Security,
                    title = "Aba Segurança (Crítico para não ficar vazio)",
                    description = "⚠️ No Windows 10 e 11, se esta etapa for esquecida a pasta fica vazia no app:\n" +
                            "1. Na mesma janela de Propriedades da pasta, clique na aba 'Segurança'.\n" +
                            "2. Clique no botão 'Editar...'.\n" +
                            "3. Se o usuário 'Todos' (ou 'Everyone') não estiver na lista, clique em 'Adicionar...', digite Todos e dê OK.\n" +
                            "4. Selecione 'Todos' e certifique-se de marcar 'Leitura e execução' e 'Listar conteúdo da pasta'. Clique em Aplicar e OK."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Passo 4: IP e Conexão
                StepItemCard(
                    stepNumber = "4",
                    icon = Icons.Default.Info,
                    title = "Como Acessar no CineLocal",
                    description = "1. Pressione Win + R no PC, digite cmd e tecle Enter. Digite 'ipconfig' para ver seu IPv4 (ex: 192.168.1.15).\n" +
                            "2. No CineLocal, use 'Buscar PCs Wi-Fi' ou 'Conectar por IP'.\n" +
                            "3. Se seu compartilhamento tiver um nome customizado, toque em 'Adicionar Compartilhamento' no topo do app e digite o nome."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onReadyToConnect()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                modifier = Modifier.testTag("ready_to_connect_button")
            ) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Entendi, Conectar PC", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun StepItemCard(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = CineRed,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stepNumber,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CineRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
