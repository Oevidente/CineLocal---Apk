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

    /**
     * Sanitiza MimeTypes para máxima compatibilidade com o Google Cast Default Media Receiver
     * e receptores com suporte a MKV / Matroska demuxer.
     */
    fun sanitizeMimeForCast(urlOrPath: String, detectedMime: String? = null): String {
        val lower = urlOrPath.lowercase()
        return when {
            lower.contains(".m3u8") || detectedMime?.contains("mpegurl", ignoreCase = true) == true -> "application/x-mpegURL"
            lower.contains(".mpd") || detectedMime?.contains("dash", ignoreCase = true) == true -> "application/dash+xml"
            lower.endsWith(".webm") || detectedMime?.contains("webm", ignoreCase = true) == true -> "video/webm"
            lower.endsWith(".mkv") || detectedMime?.contains("matroska", ignoreCase = true) == true -> "video/x-matroska"
            else -> "video/mp4"
        }
    }

    fun resolveForCast(
        context: Context,
        episode: EpisodeEntity,
        media: MediaItemEntity?,
        proxyServer: MediaProxyServer
    ): CastResolvedSource? {
        // CAST-003: Reutilização do arquivo de torrent preparado se já baixado/em buffer
        val isMagnetOrTorrent = episode.streamUrl?.startsWith("magnet:") == true ||
            media?.kind == com.example.cinelocal.data.model.MediaKind.TORRENT ||
            episode.filePath?.contains("torrents") == true

        if (isMagnetOrTorrent) {
            val torrentStatus = com.example.cinelocal.data.torrent.TorrentStreamEngine.status.value
            val preparedFile = torrentStatus.currentVideoFile
            if (preparedFile != null && preparedFile.exists() && preparedFile.isFile && preparedFile.length() > 0) {
                proxyServer.log("Reutilizando arquivo de torrent preparado (${preparedFile.length()} bytes): ${preparedFile.name}")
                val proxyUrl = proxyServer.registerMedia(Uri.fromFile(preparedFile), "video/mp4")
                return CastResolvedSource(
                    url = proxyUrl,
                    mimeType = "video/mp4",
                    streamType = MediaInfo.STREAM_TYPE_BUFFERED,
                    isLocal = true
                )
            }
        }

        val result = PlaybackSourceResolver.resolve(context, episode, media)
        if (result !is ResolveResult.Ok) {
            proxyServer.log("Falha ao resolver fonte de vídeo em PlaybackSourceResolver para o Cast: episodeId=${episode.id}")
            return null
        }
        val source = result.source

        val uri = source.uri
        val scheme = uri.scheme?.lowercase() ?: ""

        if (scheme == "http" || scheme == "https") {
            var urlStr = uri.toString()
            if (urlStr.startsWith("http://127.0.0.1", ignoreCase = true) || urlStr.startsWith("http://localhost", ignoreCase = true)) {
                val wifiIp = proxyServer.getDeviceIpAddress()
                urlStr = urlStr.replace("127.0.0.1", wifiIp).replace("localhost", wifiIp)
                proxyServer.log("Substituído endereço de loopback para IP da LAN na URL de streaming: $urlStr")
            }

            val mime = sanitizeMimeForCast(urlStr, source.mimeType)
            return CastResolvedSource(
                url = urlStr,
                mimeType = mime,
                streamType = if (source.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED,
                isLocal = true
            )
        }

        // URI Local (content:// ou file://) -> registrar no MediaProxyServer
        val detectedMime = detectMimeType(context, uri)
        val castMime = sanitizeMimeForCast(uri.toString(), detectedMime)
        logMediaTracks(context, uri, proxyServer)
        val proxyUrl = proxyServer.registerMedia(uri, castMime)

        return CastResolvedSource(
            url = proxyUrl,
            mimeType = castMime,
            streamType = MediaInfo.STREAM_TYPE_BUFFERED,
            isLocal = true
        )
    }

    fun resolveIptvForCast(
        channelUrl: String,
        proxyServer: MediaProxyServer
    ): CastResolvedSource {
        val trimmed = channelUrl.trim()
        val mime = sanitizeMimeForCast(trimmed)
        // CAST-002: URLs HTTP e HTTPS são registradas no proxy local para HLS rewriting e compatibilidade
        val proxyUrl = if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            proxyServer.registerIptvUrl(trimmed)
        } else {
            trimmed
        }
        return CastResolvedSource(
            url = proxyUrl,
            mimeType = mime,
            streamType = MediaInfo.STREAM_TYPE_LIVE,
            isLocal = true
        )
    }

    private fun logMediaTracks(context: Context, uri: Uri, proxyServer: MediaProxyServer) {
        var extractor: android.media.MediaExtractor? = null
        try {
            extractor = android.media.MediaExtractor()
            if (uri.scheme == "file") {
                extractor.setDataSource(uri.path ?: "")
            } else {
                extractor.setDataSource(context, uri, null)
            }
            val trackCount = extractor.trackCount
            val tracksList = mutableListOf<String>()
            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: "desconhecido"
                val width = if (format.containsKey(android.media.MediaFormat.KEY_WIDTH)) "${format.getInteger(android.media.MediaFormat.KEY_WIDTH)}x${format.getInteger(android.media.MediaFormat.KEY_HEIGHT)}" else ""
                val sampleRate = if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) "${format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)}Hz" else ""
                tracksList.add("$mime $width $sampleRate".trim())
            }
            proxyServer.log("Fonte local para Cast: uri=$uri detectedMime=${detectMimeType(context, uri)} tracks=${tracksList.joinToString(" | ")}")
        } catch (e: Exception) {
            proxyServer.log("Fonte local para Cast: uri=$uri (MediaExtractor: ${e.message})")
        } finally {
            try { extractor?.release() } catch (_: Exception) {}
        }
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
