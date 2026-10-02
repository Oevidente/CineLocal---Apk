package com.example.cinelocal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.example.cinelocal.cast.CastState
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
fun NativeCastButton(
    castState: CastState,
    modifier: Modifier = Modifier,
    alwaysVisible: Boolean = false
) {
    // Show Cast button if devices are available or if connected, or if alwaysVisible is true
    val isVisible = alwaysVisible || castState.hasAvailableDevices || castState.isConnected

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        AndroidView(
            factory = { context ->
                MediaRouteButton(context).apply {
                    CastButtonFactory.setUpMediaRouteButton(context, this)
                }
            },
            update = { mediaRouteButton ->
                CastButtonFactory.setUpMediaRouteButton(mediaRouteButton.context, mediaRouteButton)
            },
            modifier = Modifier
                .size(40.dp)
                .testTag("native_media_route_cast_button")
        )
    }
}
