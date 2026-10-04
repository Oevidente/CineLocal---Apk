package com.example.cinelocal.cast

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.io.File

data class MediaProbeResult(
    val containerMime: String? = null,
    val videoMime: String? = null,
    val audioMime: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val isHevc: Boolean = false,
    val is10Bit: Boolean = false,
    val audioChannels: Int = 0,
    val audioSampleRate: Int = 0,
    val summary: String = ""
)

object MediaProbe {

    suspend fun probeMedia(context: Context, uri: Uri): MediaProbeResult? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(5000L) {
            val extractor = MediaExtractor()
            try {
                when (uri.scheme) {
                    "content" -> {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            extractor.setDataSource(pfd.fileDescriptor)
                        } ?: return@withTimeoutOrNull null
                    }
                    "file" -> {
                        val file = File(uri.path ?: "")
                        if (file.exists() && file.canRead()) {
                            extractor.setDataSource(file.absolutePath)
                        } else {
                            return@withTimeoutOrNull null
                        }
                    }
                    "http", "https" -> {
                        extractor.setDataSource(uri.toString(), null)
                    }
                    else -> {
                        extractor.setDataSource(context, uri, null)
                    }
                }

                var videoMime: String? = null
                var audioMime: String? = null
                var width = 0
                var height = 0
                var frameRate = 0f
                var isHevc = false
                var is10Bit = false
                var audioChannels = 0
                var audioSampleRate = 0

                val numTracks = extractor.trackCount
                for (i in 0 until numTracks) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                    if (mime.startsWith("video/")) {
                        videoMime = mime
                        if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                            width = format.getInteger(MediaFormat.KEY_WIDTH)
                        }
                        if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                            height = format.getInteger(MediaFormat.KEY_HEIGHT)
                        }
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            try {
                                frameRate = format.getFloat(MediaFormat.KEY_FRAME_RATE)
                            } catch (_: Exception) {
                                try {
                                    frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                                } catch (_: Exception) {}
                            }
                        }

                        if (mime.contains("hevc", ignoreCase = true) || mime.contains("265", ignoreCase = true)) {
                            isHevc = true
                        }

                        if (format.containsKey(MediaFormat.KEY_PROFILE)) {
                            val profile = format.getInteger(MediaFormat.KEY_PROFILE)
                            if (profile in listOf(2, 4096, 8192)) { // Main 10 / Main 10 HDR
                                is10Bit = true
                            }
                        }
                    } else if (mime.startsWith("audio/")) {
                        audioMime = mime
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            audioChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            audioSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                    }
                }

                val vCodecStr = when {
                    isHevc && is10Bit -> "HEVC Main10"
                    isHevc -> "HEVC (H.265)"
                    videoMime?.contains("avc") == true || videoMime?.contains("264") == true -> "H.264 (AVC)"
                    videoMime?.contains("vp9") == true -> "VP9"
                    videoMime?.contains("av1") == true -> "AV1"
                    else -> videoMime?.substringAfter('/')?.uppercase() ?: "Desconhecido"
                }

                val resStr = if (width > 0 && height > 0) "${width}x${height}" else ""
                val fpsStr = if (frameRate > 0) "${frameRate.toInt()}fps" else ""

                val aCodecStr = when {
                    audioMime?.contains("mp4a") == true || audioMime?.contains("aac") == true -> "AAC"
                    audioMime?.contains("eac3") == true -> "E-AC3"
                    audioMime?.contains("ac3") == true -> "AC3"
                    audioMime?.contains("dts") == true -> "DTS"
                    audioMime?.contains("mpeg") == true -> "MP3"
                    audioMime?.contains("flac") == true -> "FLAC"
                    audioMime?.contains("opus") == true -> "Opus"
                    audioMime?.contains("vorbis") == true -> "Vorbis"
                    else -> audioMime?.substringAfter('/')?.uppercase() ?: "Áudio"
                }

                val chStr = if (audioChannels > 0) "${audioChannels}ch" else ""

                val vPart = listOf(vCodecStr, resStr, fpsStr).filter { it.isNotBlank() }.joinToString(" ")
                val aPart = listOf(aCodecStr, chStr).filter { it.isNotBlank() }.joinToString(" ")

                val summaryStr = "Vídeo: $vPart · Áudio: $aPart"

                MediaProbeResult(
                    containerMime = null,
                    videoMime = videoMime,
                    audioMime = audioMime,
                    width = width,
                    height = height,
                    frameRate = frameRate,
                    isHevc = isHevc,
                    is10Bit = is10Bit,
                    audioChannels = audioChannels,
                    audioSampleRate = audioSampleRate,
                    summary = summaryStr
                )
            } catch (e: Exception) {
                Log.w("MediaProbe", "Falha ao inspecionar mídia: ${e.message}")
                null
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }
        }
    }
}
