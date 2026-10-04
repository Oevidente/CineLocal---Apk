package com.example.cinelocal.data.torrent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object TorrentLaunchHelper {

    fun openInExternalTorrentApp(context: Context, magnetUri: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(magnetUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            try {
                // Tenta abrir no navegador ou loja se não houver cliente padrão
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetUri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "Nenhum app de torrent (como LibreTorrent ou Flud) encontrado no dispositivo.",
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }
    }

    fun isSeasonPack(title: String, magnetUri: String): Boolean {
        val lower = "$title $magnetUri".lowercase()
        val seasonRegex = Regex("""(?i)[._\s-]s(\d{1,2})""")
        val tRegex = Regex("""(?i)[._\s-]t(\d{1,2})""")
        val seasonWordRegex = Regex("""(?i)(?:temporada|season)\s*(\d{1,2})""")
        return seasonRegex.containsMatchIn(lower) ||
                tRegex.containsMatchIn(lower) ||
                seasonWordRegex.containsMatchIn(lower) ||
                lower.contains("temporada") ||
                lower.contains("season") ||
                lower.contains("complete") ||
                lower.contains("pack")
    }

    fun parseSeasonNumber(title: String, magnetUri: String): Int {
        val combined = "$title $magnetUri"
        val matchS = Regex("""(?i)[._\s-]S(\d{1,2})[._\s-]?""").find(combined)
        if (matchS != null) {
            val num = matchS.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }
        val matchT = Regex("""(?i)[._\s-]T(\d{1,2})[._\s-]?""").find(combined)
        if (matchT != null) {
            val num = matchT.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }
        val matchWord = Regex("""(?i)(?:season|temporada)\s*(\d{1,2})""").find(combined)
        if (matchWord != null) {
            val num = matchWord.groupValues[1].toIntOrNull()
            if (num != null && num > 0) return num
        }
        return 1
    }

    /**
     * Gera episódios com base em arquivos reais do torrent quando disponíveis,
     * ou detecta a numeração real da temporada/episódio (QA-003).
     */
    fun generateSeasonEpisodes(
        mediaId: String,
        seriesTitle: String,
        magnetUri: String,
        fileNames: List<String>? = null,
        seasonNumber: Int? = null
    ): List<com.example.cinelocal.data.model.EpisodeEntity> {
        val seasonNum = seasonNumber ?: parseSeasonNumber(seriesTitle, magnetUri)
        val list = mutableListOf<com.example.cinelocal.data.model.EpisodeEntity>()

        // 1. Se arquivos reais do torrent foram passados, gera episódios precisos para cada arquivo
        if (!fileNames.isNullOrEmpty()) {
            val videoExtensions = setOf("mp4", "mkv", "avi", "webm", "ts", "mov", "m4v")
            val videoFiles = fileNames.filter {
                val ext = it.substringAfterLast('.', "").lowercase()
                videoExtensions.contains(ext)
            }.sortedWith(String.CASE_INSENSITIVE_ORDER)

            if (videoFiles.isNotEmpty()) {
                videoFiles.forEachIndexed { index, fileName ->
                    val parsed = com.example.cinelocal.data.scanner.MediaNameParser.parse(fileName)
                    val epNumber = if (parsed.episodeNumber > 0) parsed.episodeNumber else (index + 1)
                    val epSeason = if (parsed.seasonNumber > 0) parsed.seasonNumber else seasonNum
                    val cleanName = fileName.substringBeforeLast('.')
                    val encodedFile = try {
                        java.net.URLEncoder.encode(fileName, "UTF-8")
                    } catch (_: Exception) {
                        fileName
                    }
                    val streamUrlWithFile = if (magnetUri.contains("?")) "$magnetUri&file=$encodedFile" else "$magnetUri?file=$encodedFile"

                    list.add(
                        com.example.cinelocal.data.model.EpisodeEntity(
                            id = java.util.UUID.randomUUID().toString(),
                            mediaId = mediaId,
                            seasonNumber = epSeason,
                            episodeNumber = epNumber,
                            title = "Episódio $epNumber - $cleanName",
                            streamUrl = streamUrlWithFile,
                            filePath = fileName,
                            progressSeconds = 0L,
                            durationSeconds = 0L
                        )
                    )
                }
                return list
            }
        }

        // 2. Verifica se o magnet ou título já aponta para um episódio individual específico (ex: S02E05)
        val singleEpMatch = Regex("""[._\s-]S(\d{1,2})E(\d{1,2})""", RegexOption.IGNORE_CASE).find("$seriesTitle $magnetUri")
        if (singleEpMatch != null) {
            val sNum = singleEpMatch.groupValues[1].toIntOrNull() ?: seasonNum
            val eNum = singleEpMatch.groupValues[2].toIntOrNull() ?: 1
            val epNumStr = if (eNum < 10) "0$eNum" else "$eNum"
            val sNumStr = if (sNum < 10) "0$sNum" else "$sNum"
            list.add(
                com.example.cinelocal.data.model.EpisodeEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    mediaId = mediaId,
                    seasonNumber = sNum,
                    episodeNumber = eNum,
                    title = "Episódio $eNum - S${sNumStr}E${epNumStr}",
                    streamUrl = magnetUri,
                    progressSeconds = 0L,
                    durationSeconds = 0L
                )
            )
            return list
        }

        // 3. Temporada inteira ainda aguardando lista de arquivos do enxame: cria streaming para a temporada identificada
        val sNumStr = if (seasonNum < 10) "0$seasonNum" else "$seasonNum"
        list.add(
            com.example.cinelocal.data.model.EpisodeEntity(
                id = java.util.UUID.randomUUID().toString(),
                mediaId = mediaId,
                seasonNumber = seasonNum,
                episodeNumber = 1,
                title = "Temporada $seasonNum - Iniciar Streaming",
                streamUrl = magnetUri,
                progressSeconds = 0L,
                durationSeconds = 0L
            )
        )
        return list
    }
}
