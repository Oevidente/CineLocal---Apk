package com.example.cinelocal.data.subtitles

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object SubtitleUtils {

    private const val TAG = "SubtitleUtils"

    /**
     * Converte o conteúdo de um arquivo SRT para o formato WebVTT.
     * Trata quebras de linha (CRLF -> LF), substitui vírgulas por pontos em timestamps
     * e garante o cabeçalho WEBVTT obrigatório.
     */
    fun convertSrtToVtt(srtContent: String): String {
        val lines = srtContent.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val vttBuilder = StringBuilder("WEBVTT\n\n")

        val timestampRegex = Regex("""(\d{2}:\d{2}:\d{2}),(\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}),(\d{3})""")

        for (line in lines) {
            val trimmed = line.trim()
            if (timestampRegex.containsMatchIn(trimmed)) {
                val convertedTimestamp = timestampRegex.replace(trimmed) { match ->
                    "${match.groupValues[1]}.${match.groupValues[2]} --> ${match.groupValues[3]}.${match.groupValues[4]}"
                }
                vttBuilder.append(convertedTimestamp).append("\n")
            } else {
                vttBuilder.append(line).append("\n")
            }
        }

        return vttBuilder.toString()
    }

    /**
     * Lê uma InputStream tentando primeiro decodificar como UTF-8; se houver caracteres inválidos
     * ou BOM malformado, realiza fallback seguro para Windows-1252 / ISO-8859-1.
     */
    fun readStreamWithEncodingFallback(inputStream: InputStream): String {
        val bytes = inputStream.readBytes()
        if (bytes.isEmpty()) return ""

        // Verificar BOM UTF-8
        val hasUtf8Bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val offset = if (hasUtf8Bom) 3 else 0

        return try {
            val utf8Decoder = StandardCharsets.UTF_8.newDecoder()
            val byteBuffer = java.nio.ByteBuffer.wrap(bytes, offset, bytes.size - offset)
            utf8Decoder.decode(byteBuffer).toString()
        } catch (_: Exception) {
            try {
                String(bytes, offset, bytes.size - offset, Charset.forName("windows-1252"))
            } catch (_: Exception) {
                String(bytes, offset, bytes.size - offset, StandardCharsets.ISO_8859_1)
            }
        }
    }

    /**
     * Salva uma legenda WebVTT no diretório de cache do aplicativo e retorna o arquivo gerado.
     */
    fun saveVttToCache(context: Context, filename: String, vttContent: String): File {
        val subDir = File(context.cacheDir, "subtitles").apply { if (!exists()) mkdirs() }
        val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(subDir, if (safeName.endsWith(".vtt")) safeName else "$safeName.vtt")
        file.writeText(vttContent, StandardCharsets.UTF_8)
        return file
    }

    /**
     * Tenta encontrar legendas locais correspondentes ao URI do vídeo.
     */
    fun findLocalSubtitleUri(context: Context, videoUri: Uri): Uri? {
        try {
            if (videoUri.scheme == "file") {
                val videoFile = File(videoUri.path ?: "")
                if (videoFile.exists()) {
                    val baseName = videoFile.nameWithoutExtension
                    val parent = videoFile.parentFile ?: return null
                    val candidateExts = listOf(".srt", ".vtt", ".pob.srt", ".pt.srt", ".pt-br.srt")
                    for (ext in candidateExts) {
                        val subFile = File(parent, "$baseName$ext")
                        if (subFile.exists() && subFile.canRead()) {
                            return Uri.fromFile(subFile)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao buscar legenda local: ${e.message}")
        }
        return null
    }
}
