@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.cinelocal.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.media3.common.MimeTypes
import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.MediaKind
import java.io.File

data class ResolvedSource(
    val uri: Uri,
    val mimeType: String? = null,
    val isLive: Boolean = false
)

sealed interface ResolveResult {
    data class Ok(val source: ResolvedSource) : ResolveResult
    data class Fail(val reason: String) : ResolveResult
}

object PlaybackSourceResolver {

    /**
     * Resolve a fonte reproduzível de um episódio e sua mídia pai na ordem estrita de prioridade:
     * 1. ep.streamUrl (http, https, rtsp)
     * 2. ep.uriString (content://, file://, http)
     * 3. ep.filePath, só se for caminho absoluto e File(path).canRead()
     * 4. Os mesmos campos em media
     * 5. Magnet -> delegar ao provedor de torrent (até lá, motor inativo)
     * 6. Nada serve -> "Este item não tem fonte de vídeo"
     */
    fun resolve(
        context: Context,
        ep: EpisodeEntity?,
        media: MediaItemEntity? = null
    ): ResolveResult {
        // Coleta candidatos em ordem de prioridade
        val candidateStrings = listOfNotNull(
            ep?.streamUrl?.takeIf { it.isNotBlank() },
            ep?.uriString?.takeIf { it.isNotBlank() },
            ep?.filePath?.takeIf { it.isNotBlank() && isReadableFile(it) },
            media?.streamUrl?.takeIf { it.isNotBlank() },
            media?.uriString?.takeIf { it.isNotBlank() },
            media?.filePath?.takeIf { it.isNotBlank() && isReadableFile(it) }
        )

        // Verifica se é torrent/magnet
        val hasMagnetCandidate = candidateStrings.any { it.startsWith("magnet:", ignoreCase = true) } ||
            media?.kind == MediaKind.TORRENT ||
            (media?.streamUrl?.startsWith("magnet:", ignoreCase = true) == true) ||
            (ep?.streamUrl?.startsWith("magnet:", ignoreCase = true) == true)

        // Filtra "file://null", "null" literais e magnets
        val validCandidate = candidateStrings.firstOrNull { cand ->
            cand != "file://null" &&
                cand != "null" &&
                !cand.startsWith("magnet:", ignoreCase = true)
        }

        if (validCandidate != null) {
            val uri = Uri.parse(validCandidate)

            // Rejeição obrigatória: URI de árvore SAF (pasta)
            if (isTreeUriNotDocument(context, uri)) {
                return ResolveResult.Fail("Isto é uma pasta, não um vídeo. Reescaneie a pasta.")
            }

            val mime = inferMimeType(validCandidate)
            return ResolveResult.Ok(
                ResolvedSource(
                    uri = uri,
                    mimeType = mime,
                    isLive = false
                )
            )
        }

        val magnetUri = candidateStrings.firstOrNull { it.startsWith("magnet:", ignoreCase = true) }
            ?: if (hasMagnetCandidate) (media?.streamUrl ?: ep?.streamUrl) else null

        if (magnetUri != null && magnetUri.startsWith("magnet:", ignoreCase = true)) {
            com.example.cinelocal.data.torrent.TorrentStreamEngine.startStream(context, magnetUri)
            val currentFile = com.example.cinelocal.data.torrent.TorrentStreamEngine.status.value.currentVideoFile
            if (currentFile != null && currentFile.exists()) {
                return ResolveResult.Ok(
                    ResolvedSource(
                        uri = Uri.fromFile(currentFile),
                        mimeType = inferMimeType(currentFile.name) ?: MimeTypes.VIDEO_MP4,
                        isLive = false
                    )
                )
            } else {
                return ResolveResult.Ok(
                    ResolvedSource(
                        uri = Uri.parse("torrent://${com.example.cinelocal.data.torrent.TorrentUtils.extractInfoHash(magnetUri) ?: "p2p"}"),
                        mimeType = MimeTypes.VIDEO_MP4,
                        isLive = false
                    )
                )
            }
        }

        return ResolveResult.Fail("Este item não tem fonte de vídeo.")
    }




    /**
     * Resolve uma transmissão ao vivo (IPTV/HLS).
     */
    fun resolveLive(streamUrl: String): ResolveResult {
        val trimmed = streamUrl.trim()
        if (trimmed.isBlank() || trimmed == "null" || trimmed == "file://null") {
            return ResolveResult.Fail("Endereço de transmissão inválido ou vazio.")
        }
        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return ResolveResult.Fail("URL de transmissão malformada.")
        }
        val mime = inferMimeType(trimmed)
        return ResolveResult.Ok(
            ResolvedSource(
                uri = uri,
                mimeType = mime,
                isLive = true
            )
        )
    }

    private fun isReadableFile(path: String): Boolean {
        return try {
            val file = File(path)
            file.isAbsolute && file.exists() && file.canRead()
        } catch (_: Exception) {
            false
        }
    }

    private fun isTreeUriNotDocument(context: Context, uri: Uri): Boolean {
        return try {
            if (DocumentsContract.isTreeUri(uri)) {
                !DocumentsContract.isDocumentUri(context, uri)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun inferMimeType(url: String): String? {
        val lowerUrl = url.lowercase()
        val fileParam = if (url.contains("f=")) {
            url.substringAfter("f=").substringBefore('&').lowercase()
        } else ""
        val cleanUrl = lowerUrl.substringBefore('?')

        return when {
            cleanUrl.endsWith(".m3u8") || lowerUrl.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            cleanUrl.endsWith(".mpd") || lowerUrl.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            cleanUrl.endsWith(".mp4") || cleanUrl.endsWith(".m4v") || fileParam.endsWith(".mp4") || fileParam.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
            cleanUrl.endsWith(".mkv") || fileParam.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            cleanUrl.endsWith(".webm") || fileParam.endsWith(".webm") -> MimeTypes.VIDEO_WEBM
            cleanUrl.endsWith(".avi") || fileParam.endsWith(".avi") -> "video/x-msvideo"
            cleanUrl.endsWith(".mov") || fileParam.endsWith(".mov") -> "video/quicktime"
            cleanUrl.endsWith(".ts") || fileParam.endsWith(".ts") -> "video/mp2t"
            else -> null
        }
    }
}

