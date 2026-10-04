package com.example.cinelocal.data.scanner

import android.util.Log
import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.MediaKind
import com.example.cinelocal.data.repository.MediaRepository
import com.example.cinelocal.data.smb.SmbClientManager
import com.example.cinelocal.data.smb.SmbConnectionConfig
import com.example.cinelocal.data.smb.SmbFileItem
import com.example.cinelocal.data.smb.SmbStreamProxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

object SmbFolderScanner {

    private const val TAG = "SmbFolderScanner"
    private const val MAX_DEPTH = 6
    private const val MIN_SAMPLE_SIZE_BYTES = 50 * 1024 * 1024L

    suspend fun scanAndImportFolder(
        repository: MediaRepository,
        config: SmbConnectionConfig,
        shareName: String,
        directoryPath: String,
        onProgress: ((count: Int, currentFile: String) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val collectedVideos = mutableListOf<SmbFileItem>()
        scanSmbDirectoryRecursive(
            config = config,
            shareName = shareName,
            dirPath = directoryPath,
            depth = 0,
            results = collectedVideos,
            onProgress = onProgress
        )

        // Agrupar por título e tipo
        val seriesGroups = mutableMapOf<String, MutableList<Pair<SmbFileItem, ParsedMediaName>>>()
        val moviesList = mutableListOf<Pair<SmbFileItem, ParsedMediaName>>()

        for (video in collectedVideos) {
            val pathSegments = video.path.trim('/').split('/')
            val parentName = if (pathSegments.size >= 2) pathSegments[pathSegments.size - 2] else null
            val grandParentName = if (pathSegments.size >= 3) pathSegments[pathSegments.size - 3] else null
            val parsed = MediaNameParser.parse(video.name, parentName?.ifBlank { null }, grandParentName?.ifBlank { null })
            if (parsed.isSeries) {
                val groupKey = parsed.title.lowercase().trim()
                seriesGroups.getOrPut(groupKey) { mutableListOf() }.add(Pair(video, parsed))
            } else {
                moviesList.add(Pair(video, parsed))
            }
        }

        var importedCount = 0

        // Inserir filmes
        for ((video, parsed) in moviesList) {
            val streamUrl = SmbStreamProxy.createProxyUrl(config, shareName, video.path)
            val mediaId = UUID.randomUUID().toString()
            val media = MediaItemEntity(
                id = mediaId,
                title = parsed.title,
                kind = MediaKind.MOVIE,
                year = parsed.year,
                streamUrl = streamUrl,
                filePath = "smb://${config.host}/$shareName/${video.path}",
                overview = "Armazenado no computador: ${config.host} ($shareName)",
                addedTimestamp = System.currentTimeMillis()
            )
            val episode = EpisodeEntity(
                id = UUID.randomUUID().toString(),
                mediaId = mediaId,
                seasonNumber = 0,
                episodeNumber = 1,
                title = parsed.title,
                streamUrl = streamUrl,
                filePath = "smb://${config.host}/$shareName/${video.path}",
                durationSeconds = 0L
            )
            repository.insertMediaWithEpisodes(media, listOf(episode))
            importedCount++
        }

        // Inserir séries
        for ((_, episodesList) in seriesGroups) {
            val sampleParsed = episodesList.first().second
            val seriesTitle = sampleParsed.title
            val mediaId = UUID.randomUUID().toString()
            val totalSeasons = episodesList.maxOfOrNull { it.second.seasonNumber } ?: 1
            val totalEpisodes = episodesList.size

            val media = MediaItemEntity(
                id = mediaId,
                title = seriesTitle,
                kind = MediaKind.SERIES,
                year = sampleParsed.year,
                totalSeasons = totalSeasons,
                totalEpisodes = totalEpisodes,
                overview = "Série armazenada no computador: ${config.host} ($shareName)",
                addedTimestamp = System.currentTimeMillis()
            )

            val episodeEntities = episodesList.map { (video, parsed) ->
                val streamUrl = SmbStreamProxy.createProxyUrl(config, shareName, video.path)
                EpisodeEntity(
                    id = UUID.randomUUID().toString(),
                    mediaId = mediaId,
                    seasonNumber = parsed.seasonNumber,
                    episodeNumber = parsed.episodeNumber,
                    title = parsed.title,
                    streamUrl = streamUrl,
                    filePath = "smb://${config.host}/$shareName/${video.path}",
                    durationSeconds = 0L
                )
            }.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))

            repository.insertMediaWithEpisodes(media, episodeEntities)
            importedCount++
        }

        importedCount
    }

    private suspend fun scanSmbDirectoryRecursive(
        config: SmbConnectionConfig,
        shareName: String,
        dirPath: String,
        depth: Int,
        results: MutableList<SmbFileItem>,
        onProgress: ((count: Int, currentFile: String) -> Unit)?
    ) {
        if (depth > MAX_DEPTH) return

        val items = SmbClientManager.listDirectory(config, shareName, dirPath)
        for (item in items) {
            if (item.isDirectory) {
                scanSmbDirectoryRecursive(
                    config = config,
                    shareName = shareName,
                    dirPath = item.path,
                    depth = depth + 1,
                    results = results,
                    onProgress = onProgress
                )
            } else if (item.isVideo) {
                val lowerName = item.name.lowercase()
                if ((lowerName.contains("sample") || lowerName.contains("trailer")) && item.sizeBytes > 0 && item.sizeBytes < MIN_SAMPLE_SIZE_BYTES) {
                    continue
                }
                results.add(item)
                onProgress?.invoke(results.size, item.name)
            }
        }
    }
}
