package com.example.cinelocal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.cinelocal.cast.CastState
import com.example.cinelocal.ui.theme.AccentGold
import com.example.cinelocal.ui.theme.CineRed

@Composable
fun CastButton(
    castState: CastState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    val iconColor by animateColorAsState(
        targetValue = if (castState.isConnected) AccentGold else tint,
        label = "cast_icon_tint"
    )

    IconButton(
        onClick = onClick,
        modifier = modifier.testTag("google_cast_button")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (castState.isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                contentDescription = if (castState.isConnected) "Conectado ao Cast: ${castState.deviceName}" else "Transmitir via Google Cast",
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            if (castState.isConnected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(AccentGold, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }
    }
}
