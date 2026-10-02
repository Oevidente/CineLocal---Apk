package com.example.cinelocal.ui.components

import android.view.ContextThemeWrapper
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
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            val themedContext = ContextThemeWrapper(context, androidx.mediarouter.R.style.Theme_MediaRouter)
            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(context, this)
            }
        },
        update = { mediaRouteButton ->
            CastButtonFactory.setUpMediaRouteButton(mediaRouteButton.context, mediaRouteButton)
        },
        modifier = modifier
            .size(40.dp)
            .testTag("native_media_route_cast_button")
    )
}
