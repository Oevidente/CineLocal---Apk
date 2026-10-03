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
        return lower.contains(".s01.") ||
                lower.contains(".s02.") ||
                lower.contains(".s03.") ||
                lower.contains(".s04.") ||
                lower.contains(".s05.") ||
                lower.contains("temporada") ||
                lower.contains("season") ||
                lower.contains("complete") ||
                lower.contains("pack")
    }

    fun generateSeasonEpisodes(
        mediaId: String,
        seriesTitle: String,
        magnetUri: String,
        estimatedEpisodes: Int = 8
    ): List<com.example.cinelocal.data.model.EpisodeEntity> {
        val list = mutableListOf<com.example.cinelocal.data.model.EpisodeEntity>()
        val seasonNum = 1

        for (i in 1..estimatedEpisodes) {
            val epNumStr = if (i < 10) "0$i" else "$i"
            val title = "Episódio $i - S01E$epNumStr"
            list.add(
                com.example.cinelocal.data.model.EpisodeEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    mediaId = mediaId,
                    seasonNumber = seasonNum,
                    episodeNumber = i,
                    title = title,
                    streamUrl = magnetUri,
                    progressSeconds = 0L,
                    durationSeconds = 0L
                )
            )
        }
        return list
    }
}
