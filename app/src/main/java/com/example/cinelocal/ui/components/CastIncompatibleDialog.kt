package com.example.cinelocal.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cinelocal.ui.theme.AccentGold
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary

@Composable
fun CastIncompatibleDialog(
    warningMessage: String,
    onPlayOnPhone: () -> Unit,
    onTransmitAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showFfmpegGuide by remember { mutableStateOf(false) }

    fun copyFfmpegToClipboard(cmd: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("FFmpeg Command", cmd)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Comando copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
    }

    val ffmpegConvertCmd = "ffmpeg -i entrada.mkv -c:v libx264 -profile:v high -level 4.1 -pix_fmt yuv420p -crf 20 -c:a aac -b:a 192k -movflags +faststart saida.mp4"
    val ffmpegAudioCopyCmd = "ffmpeg -i entrada.mkv -c:v copy -c:a aac -b:a 192k -movflags +faststart saida.mp4"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Aviso de Compatibilidade",
                    tint = CineRed,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Aviso de Compatibilidade",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = warningMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (showFfmpegGuide) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Como converter para MP4 (H.264 + AAC):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Para converter vídeo HEVC completo:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = ffmpegConvertCmd,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            TextButton(
                                onClick = { copyFfmpegToClipboard(ffmpegConvertCmd) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = CineRed)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar Comando Completo", fontSize = 11.sp, color = CineRed)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "2. Se o vídeo já for H.264 e só o áudio for incompatível (rápido):",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = ffmpegAudioCopyCmd,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            TextButton(
                                onClick = { copyFfmpegToClipboard(ffmpegAudioCopyCmd) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = CineRed)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar Comando Áudio", fontSize = 11.sp, color = CineRed)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                } else {
                    OutlinedButton(
                        onClick = { showFfmpegGuide = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Como converter este arquivo", color = AccentGold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayOnPhone,
                    colors = ButtonDefaults.buttonColors(containerColor = CineRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Tocar no Celular", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextSecondary)
                    }
                    TextButton(onClick = onTransmitAnyway) {
                        Text("Transmitir mesmo assim", color = AccentGold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}
