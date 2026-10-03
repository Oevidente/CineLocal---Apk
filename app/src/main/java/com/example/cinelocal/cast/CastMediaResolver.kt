package com.example.cinelocal.cast

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.player.PlaybackSourceResolver
import com.example.cinelocal.player.ResolveResult
import com.google.android.gms.cast.MediaInfo

data class CastResolvedSource(
    val url: String,
    val mimeType: String,
    val streamType: Int = MediaInfo.STREAM_TYPE_BUFFERED,
    val isLocal: Boolean = false
)

object CastMediaResolver {

    fun resolveForCast(
        context: Context,
        episode: EpisodeEntity,
        media: MediaItemEntity?,
        proxyServer: MediaProxyServer
    ): CastResolvedSource? {
        val result = PlaybackSourceResolver.resolve(context, episode, media)
        if (result !is ResolveResult.Ok) return null
        val source = result.source

        val uri = source.uri
        val scheme = uri.scheme?.lowercase() ?: ""

        if (scheme == "http" || scheme == "https") {
            var urlStr = uri.toString()
            if (urlStr.startsWith("http://127.0.0.1", ignoreCase = true) || urlStr.startsWith("http://localhost", ignoreCase = true)) {
                val wifiIp = com.example.cinelocal.data.torrent.LocalNetworkUtils.getLocalIpAddress(context)
                urlStr = urlStr.replace("127.0.0.1", wifiIp).replace("localhost", wifiIp)
            }

            val mime = when {
                urlStr.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
                urlStr.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
                urlStr.endsWith(".mkv", ignoreCase = true) -> "video/x-matroska"
                else -> source.mimeType ?: "video/mp4"
            }
            return CastResolvedSource(
                url = urlStr,
                mimeType = mime,
                streamType = if (source.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED,
                isLocal = true
            )
        }


        // Local URI (content:// ou file://) -> registrar no MediaProxyServer
        val detectedMime = detectMimeType(context, uri)
        val castMime = when {
            detectedMime.contains("matroska", ignoreCase = true) -> "video/mp4"
            detectedMime.contains("webm", ignoreCase = true) -> "video/webm"
            detectedMime.isBlank() || detectedMime == "application/octet-stream" -> "video/mp4"
            else -> detectedMime
        }
        val proxyUrl = proxyServer.registerMedia(uri, castMime)

        return CastResolvedSource(
            url = proxyUrl,
            mimeType = castMime,
            streamType = MediaInfo.STREAM_TYPE_BUFFERED,
            isLocal = true
        )
    }

    private fun detectMimeType(context: Context, uri: Uri): String {
        try {
            val crType = context.contentResolver.getType(uri)
            if (!crType.isNullOrBlank() && crType != "application/octet-stream") {
                return crType
            }
        } catch (_: Exception) {}

        val path = uri.path ?: uri.toString()
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext == "mkv") return "video/x-matroska"
        if (ext == "mp4" || ext == "m4v") return "video/mp4"
        if (ext == "webm") return "video/webm"

        val fromMap = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return fromMap ?: "video/mp4"
    }
}
