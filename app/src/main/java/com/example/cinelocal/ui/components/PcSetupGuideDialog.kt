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
                        text = "Como Preparar o PC",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Passo a passo no Windows para liberar suas pastas",
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
                // Aviso de mesma rede
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
                            text = "Importante: Seu celular e seu computador devem estar conectados no mesmo roteador / rede Wi-Fi!",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Passo 1
                StepItemCard(
                    stepNumber = "1",
                    icon = Icons.Default.Router,
                    title = "Ativar Descoberta de Rede no Windows",
                    description = "1. Abra as Configurações do Windows (ou Painel de Controle).\n" +
                            "2. Vá em Rede e Internet > Configurações avançadas de rede > Compartilhamento avançado.\n" +
                            "3. Ative as opções:\n" +
                            "   • 'Descoberta de rede'\n" +
                            "   • 'Compartilhamento de arquivos e impressoras'\n" +
                            "4. Certifique-se de que a rede está definida como 'Rede Privada'."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Passo 2
                StepItemCard(
                    stepNumber = "2",
                    icon = Icons.Default.FolderShared,
                    title = "Compartilhar a Pasta de Filmes / Vídeos",
                    description = "1. No Windows Explorer, clique com o botão direito na pasta que contém seus vídeos (ex: C:\\Filmes).\n" +
                            "2. Selecione Propriedades > Aba Compartilhamento.\n" +
                            "3. Clique no botão 'Compartilhar...'.\n" +
                            "4. No campo de texto, digite 'Todos' (ou selecione seu usuário) e clique em Adicionar.\n" +
                            "5. Defina a permissão como 'Leitura' e clique em Compartilhar."
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Passo 3
                StepItemCard(
                    stepNumber = "3",
                    icon = Icons.Default.Info,
                    title = "Como Saber o IP do Computador",
                    description = "1. No teclado, pressione Win + R, digite cmd e aperte Enter.\n" +
                            "2. Na tela preta, digite: ipconfig e tecle Enter.\n" +
                            "3. Veja o número no campo 'Endereço IPv4' (exemplo: 192.168.1.15).\n" +
                            "4. Esse é o endereço que você pode usar para conectar!"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Passo 4
                StepItemCard(
                    stepNumber = "4",
                    icon = Icons.Default.LockOpen,
                    title = "Usuário e Senha de Acesso",
                    description = "• Se o Windows pedir senha: use o nome do seu usuário do Windows e a senha que você usa para entrar no PC.\n" +
                            "• Se quiser acesso livre sem senha: no Compartilhamento Avançado do Windows, marque 'Desativar compartilhamento protegido por senha' e marque 'Acesso Anônimo/Convidado' no app."
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
