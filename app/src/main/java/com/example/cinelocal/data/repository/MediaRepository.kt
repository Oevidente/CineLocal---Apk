package com.example.cinelocal.data.repository

import android.content.Context
import android.net.Uri
import com.example.cinelocal.data.db.AppDatabase
import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.IptvChannelEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.MediaKind
import com.example.cinelocal.data.model.MediaWithEpisodes
import com.example.cinelocal.data.model.SettingEntity
import com.example.cinelocal.data.torrent.TorrentUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import android.provider.OpenableColumns
import com.example.cinelocal.data.scanner.FolderScanner
import com.example.cinelocal.data.scanner.MediaNameParser
import java.util.UUID

class MediaRepository(
    private val context: Context?,
    private val database: AppDatabase
) {
    constructor(database: AppDatabase) : this(null, database)

    private val mediaDao = database.mediaDao()
    private val episodeDao = database.episodeDao()
    private val iptvChannelDao = database.iptvChannelDao()
    private val settingDao = database.settingDao()
    private val networkServerDao = database.networkServerDao()

    val allNetworkServers: Flow<List<com.example.cinelocal.data.model.NetworkServerEntity>> = networkServerDao.getAllServers()
    val allMedia: Flow<List<MediaItemEntity>> = mediaDao.getAllMedia()
    val allMediaWithEpisodes: Flow<List<MediaWithEpisodes>> = mediaDao.getAllMediaWithEpisodes()
    val allChannels: Flow<List<IptvChannelEntity>> = iptvChannelDao.getAllChannels()
    val favoriteChannels: Flow<List<IptvChannelEntity>> = iptvChannelDao.getFavoriteChannels()

    val channelGroups: Flow<List<String>> = allChannels.map { list ->
        val groups = list.map { it.group }.distinct().sorted()
        listOf("Todos") + groups
    }

    val movies: Flow<List<MediaItemEntity>> = mediaDao.getAllMedia().map { list ->
        list.filter { it.kind == MediaKind.MOVIE }
    }

    val series: Flow<List<MediaItemEntity>> = mediaDao.getAllMedia().map { list ->
        list.filter { it.kind == MediaKind.SERIES }
    }

    val torrents: Flow<List<MediaItemEntity>> = mediaDao.getAllMedia().map { list ->
        list.filter { it.kind == MediaKind.TORRENT }
    }

    val favorites: Flow<List<MediaItemEntity>> = mediaDao.getAllMedia().map { list ->
        list.filter { it.isFavorite }
    }

    val continueWatchingEpisodes: Flow<List<EpisodeEntity>> = allMediaWithEpisodes.map { list ->
        list.flatMap { it.episodes }.filter { it.progressSeconds > 0 && it.progressSeconds < it.durationSeconds }
    }

    fun getMediaWithEpisodes(id: String): Flow<MediaWithEpisodes?> = mediaDao.getMediaWithEpisodesById(id)

    suspend fun getMediaById(id: String): MediaItemEntity? = mediaDao.getMediaById(id)

    suspend fun getEpisodeById(id: String): EpisodeEntity? = episodeDao.getEpisodeById(id)

    suspend fun getChannelById(id: String): IptvChannelEntity? = iptvChannelDao.getChannelById(id)

    suspend fun toggleMediaFavorite(id: String, isFavorite: Boolean) {
        mediaDao.toggleFavorite(id, isFavorite)
    }

    suspend fun toggleChannelFavorite(id: String, isFavorite: Boolean) {
        iptvChannelDao.toggleFavorite(id, isFavorite)
    }

    suspend fun deleteMedia(id: String) {
        mediaDao.deleteMediaById(id)
    }

    suspend fun deleteMedia(id: Long) {
        mediaDao.deleteMediaById(id.toString())
    }

    suspend fun saveNetworkServer(server: com.example.cinelocal.data.model.NetworkServerEntity) {
        networkServerDao.insertServer(server)
    }

    suspend fun deleteNetworkServer(server: com.example.cinelocal.data.model.NetworkServerEntity) {
        networkServerDao.deleteServer(server)
    }

    suspend fun insertMediaWithEpisodes(media: MediaItemEntity, episodes: List<EpisodeEntity>) {
        mediaDao.insertMedia(media)
        episodes.forEach { episodeDao.insertEpisode(it) }
    }

    suspend fun importSmbFolder(
        config: com.example.cinelocal.data.smb.SmbConnectionConfig,
        shareName: String,
        dirPath: String
    ): Int = withContext(Dispatchers.IO) {
        com.example.cinelocal.data.scanner.SmbFolderScanner.scanAndImportFolder(
            repository = this@MediaRepository,
            config = config,
            shareName = shareName,
            directoryPath = dirPath
        )
    }

    suspend fun deleteChannel(id: String) {
        iptvChannelDao.deleteChannelById(id)
    }

    suspend fun addStandaloneVideoFiles(uris: List<Uri>, context: Context): List<MediaItemEntity> = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val addedItems = mutableListOf<MediaItemEntity>()

        for (uri in uris) {
            var displayName = "video"
            try {
                cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) displayName = cursor.getString(nameIdx) ?: "video"
                    }
                }
            } catch (_: Exception) {}

            val parsed = MediaNameParser.parse(displayName)
            val mediaId = UUID.randomUUID().toString()

            if (parsed.isSeries) {
                val existingList = mediaDao.getAllMediaList()
                val existingSeries = existingList.find { it.kind == MediaKind.SERIES && it.title.equals(parsed.title, ignoreCase = true) }
                val targetMediaId = existingSeries?.id ?: mediaId

                val seriesItem = if (existingSeries == null) {
                    val s = MediaItemEntity(
                        id = targetMediaId,
                        title = parsed.title,
                        kind = MediaKind.SERIES,
                        year = parsed.year,
                        overview = "Série importada de arquivo local.",
                        uriString = uri.toString()
                    )
                    mediaDao.insertMedia(s)
                    s
                } else existingSeries
                addedItems.add(seriesItem)

                val ep = EpisodeEntity(
                    mediaId = targetMediaId,
                    seasonNumber = parsed.seasonNumber,
                    episodeNumber = parsed.episodeNumber,
                    title = "Episódio ${parsed.episodeNumber}",
                    uriString = uri.toString()
                )
                episodeDao.insertEpisode(ep)
            } else {
                val movieItem = MediaItemEntity(
                    id = mediaId,
                    title = parsed.title,
                    kind = MediaKind.MOVIE,
                    year = parsed.year,
                    overview = "Filme importado de arquivo local ($displayName).",
                    uriString = uri.toString()
                )
                mediaDao.insertMedia(movieItem)

                val ep = EpisodeEntity(
                    mediaId = mediaId,
                    seasonNumber = 0,
                    episodeNumber = 1,
                    title = parsed.title,
                    uriString = uri.toString()
                )
                episodeDao.insertEpisode(ep)
                addedItems.add(movieItem)
            }
        }
        addedItems
    }

    suspend fun addFolderByUri(uri: Uri, customTitle: String? = null): MediaItemEntity = withContext(Dispatchers.IO) {
        val folderName = when {
            !customTitle.isNullOrBlank() -> customTitle
            else -> uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Pasta Local"
        }

        // Se o context estiver disponível, escaneia os vídeos reais da pasta
        var scannedCount = 0
        if (context != null) {
            val scanned = FolderScanner.scanTree(context.contentResolver, uri)
            scannedCount = scanned.size
            for (file in scanned) {
                val parsed = MediaNameParser.parse(file.displayName, file.parentFolder)
                val mediaId = UUID.randomUUID().toString()

                if (parsed.isSeries) {
                    val existing = mediaDao.getAllMediaList().find { it.kind == MediaKind.SERIES && it.title.equals(parsed.title, ignoreCase = true) }
                    val targetMediaId = existing?.id ?: mediaId
                    if (existing == null) {
                        mediaDao.insertMedia(
                            MediaItemEntity(
                                id = targetMediaId,
                                title = parsed.title,
                                kind = MediaKind.SERIES,
                                year = parsed.year,
                                overview = "Série da pasta ${file.parentFolder ?: folderName}",
                                uriString = file.uri.toString()
                            )
                        )
                    }
                    episodeDao.insertEpisode(
                        EpisodeEntity(
                            mediaId = targetMediaId,
                            seasonNumber = parsed.seasonNumber,
                            episodeNumber = parsed.episodeNumber,
                            title = "Episódio ${parsed.episodeNumber} - ${file.displayName.substringBeforeLast('.')}",
                            uriString = file.uri.toString()
                        )
                    )
                } else {
                    mediaDao.insertMedia(
                        MediaItemEntity(
                            id = mediaId,
                            title = parsed.title,
                            kind = MediaKind.MOVIE,
                            year = parsed.year,
                            overview = "Filme da pasta $folderName.",
                            uriString = file.uri.toString()
                        )
                    )
                    episodeDao.insertEpisode(
                        EpisodeEntity(
                            mediaId = mediaId,
                            seasonNumber = 0,
                            episodeNumber = 1,
                            title = parsed.title,
                            uriString = file.uri.toString()
                        )
                    )
                }
            }
        }

        val mediaId = UUID.randomUUID().toString()
        val mediaItem = MediaItemEntity(
            id = mediaId,
            title = folderName,
            kind = MediaKind.MOVIE,
            overview = "Pasta adicionada ($scannedCount vídeos encontrados).",
            uriString = uri.toString(),
            filePath = uri.path
        )
        mediaDao.insertMedia(mediaItem)
        mediaItem
    }

    suspend fun addDirectStream(title: String, url: String, kind: MediaKind) = withContext(Dispatchers.IO) {
        val mediaId = UUID.randomUUID().toString()
        val mediaItem = MediaItemEntity(
            id = mediaId,
            title = title.ifBlank { "Stream Direct" },
            kind = kind,
            overview = "Transmissão HTTP/HLS direta.",
            streamUrl = url
        )
        mediaDao.insertMedia(mediaItem)

        val episode = EpisodeEntity(
            mediaId = mediaId,
            seasonNumber = if (kind == MediaKind.SERIES) 1 else 0,
            episodeNumber = 1,
            title = if (kind == MediaKind.SERIES) "Episódio 1 - Transmissão" else (title.ifBlank { "Filme" }),
            streamUrl = url
        )
        episodeDao.insertEpisode(episode)
    }

    suspend fun addTorrentMedia(magnetUri: String, customTitle: String? = null, isSeries: Boolean = false) = withContext(Dispatchers.IO) {
        val parsed = TorrentUtils.parseMagnetUri(magnetUri)
        val infoHash = parsed?.infoHash ?: TorrentUtils.extractInfoHash(magnetUri) ?: ""
        val title = when {
            !customTitle.isNullOrBlank() -> customTitle
            parsed != null && parsed.displayName.isNotBlank() -> parsed.displayName
            else -> "Torrent ($infoHash)"
        }
        val mediaId = UUID.randomUUID().toString()
        val detectedSeries = isSeries || com.example.cinelocal.data.torrent.TorrentLaunchHelper.isSeasonPack(title, magnetUri)
        val kind = MediaKind.TORRENT

        val mediaItem = MediaItemEntity(
            id = mediaId,
            title = title,
            kind = kind,
            overview = "Filme/Série via Torrent Magnet Link (infoHash: $infoHash)",
            streamUrl = magnetUri,
            infoHash = infoHash,
            uriString = null
        )
        mediaDao.insertMedia(mediaItem)

        if (detectedSeries) {
            val episodes = com.example.cinelocal.data.torrent.TorrentLaunchHelper.generateSeasonEpisodes(mediaId, title, magnetUri, 8)
            episodes.forEach { episodeDao.insertEpisode(it) }
        } else {
            val episode = EpisodeEntity(
                mediaId = mediaId,
                seasonNumber = 0,
                episodeNumber = 1,
                title = title,
                streamUrl = magnetUri
            )
            episodeDao.insertEpisode(episode)
        }
    }


    suspend fun importIptvFromUrl(url: String, clearExisting: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (clearExisting) {
            iptvChannelDao.deleteAllChannels()
        }
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val channels = mutableListOf<IptvChannelEntity>()
            var currentName = ""
            var currentGroup = "Geral"
            var currentLogo: String? = null

            reader.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    currentName = trimmed.substringAfterLast(',').trim()
                    val groupMatch = Regex("group-title=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(trimmed)
                    currentGroup = groupMatch?.groupValues?.getOrNull(1) ?: "Geral"
                    val logoMatch = Regex("tvg-logo=\"([^\"]+)\"", RegexOption.IGNORE_CASE).find(trimmed)
                    currentLogo = logoMatch?.groupValues?.getOrNull(1)
                } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                    if (currentName.isBlank()) {
                        currentName = "Canal IPTV ${channels.size + 1}"
                    }
                    channels.add(
                        IptvChannelEntity(
                            name = currentName,
                            group = currentGroup,
                            logo = currentLogo,
                            url = trimmed
                        )
                    )
                    currentName = ""
                    currentGroup = "Geral"
                    currentLogo = null
                }
            }
            reader.close()
            if (channels.isNotEmpty()) {
                iptvChannelDao.insertChannels(channels)
            }
            channels.size
        } catch (_: Exception) {
            0
        }
    }

    suspend fun addSingleIptvChannel(name: String, url: String, group: String) = withContext(Dispatchers.IO) {
        val channel = IptvChannelEntity(
            name = name.ifBlank { "Canal IPTV" },
            group = group.ifBlank { "Geral" },
            url = url
        )
        iptvChannelDao.insertChannel(channel)
    }

    suspend fun updatePlaybackProgress(
        mediaId: String,
        episodeId: String?,
        progressSeconds: Long,
        durationSeconds: Long,
        watched: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (episodeId != null) {
            episodeDao.updateProgress(episodeId, progressSeconds, durationSeconds)
        }
        mediaDao.updateProgress(mediaId, progressSeconds, durationSeconds)
    }

    fun getSetting(key: String): Flow<String?> {
        return settingDao.getSettingFlow(key).map { it?.value }
    }

    suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        settingDao.setSetting(SettingEntity(key, value))
    }

    suspend fun loadInitialDataIfEmpty(context: Context? = null) = withContext(Dispatchers.IO) {
    }

    suspend fun rescanAll(context: Context? = null) = withContext(Dispatchers.IO) {
    }
}
