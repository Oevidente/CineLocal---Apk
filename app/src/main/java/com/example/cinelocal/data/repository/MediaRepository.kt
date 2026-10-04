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
import kotlinx.coroutines.flow.firstOrNull
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
    private val subtitleFileDao = database.subtitleFileDao()

    fun getSubtitlesForEpisode(episodeId: String): Flow<List<com.example.cinelocal.data.model.SubtitleFileEntity>> {
        return subtitleFileDao.getSubtitlesForEpisode(episodeId)
    }

    suspend fun getSubtitlesListForEpisode(episodeId: String): List<com.example.cinelocal.data.model.SubtitleFileEntity> = withContext(Dispatchers.IO) {
        subtitleFileDao.getSubtitlesListForEpisode(episodeId)
    }

    suspend fun saveSubtitleFile(subtitle: com.example.cinelocal.data.model.SubtitleFileEntity) = withContext(Dispatchers.IO) {
        subtitleFileDao.insertSubtitle(subtitle)
    }

    suspend fun deleteSubtitleFile(id: String) = withContext(Dispatchers.IO) {
        subtitleFileDao.deleteSubtitleById(id)
    }

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

    suspend fun addFolderByUri(uri: Uri, customTitle: String? = null): MediaItemEntity? = withContext(Dispatchers.IO) {
        val folderName = when {
            !customTitle.isNullOrBlank() -> customTitle
            else -> uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Pasta Local"
        }

        val addedMedia = mutableListOf<MediaItemEntity>()

        // Se o context estiver disponível, escaneia os vídeos reais da pasta
        if (context != null) {
            val scanned = FolderScanner.scanTree(context.contentResolver, uri)
            for (file in scanned) {
                val parsed = MediaNameParser.parse(file.displayName, file.parentFolder)
                val mediaId = UUID.randomUUID().toString()

                if (parsed.isSeries) {
                    val existing = mediaDao.getAllMediaList().find { it.kind == MediaKind.SERIES && it.title.equals(parsed.title, ignoreCase = true) }
                    val targetMediaId = existing?.id ?: mediaId
                    val seriesItem = existing ?: MediaItemEntity(
                        id = targetMediaId,
                        title = parsed.title,
                        kind = MediaKind.SERIES,
                        year = parsed.year,
                        overview = "Série da pasta ${file.parentFolder ?: folderName}",
                        uriString = file.uri.toString()
                    )
                    if (existing == null) {
                        mediaDao.insertMedia(seriesItem)
                    }
                    addedMedia.add(seriesItem)

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
                    val movieItem = MediaItemEntity(
                        id = mediaId,
                        title = parsed.title,
                        kind = MediaKind.MOVIE,
                        year = parsed.year,
                        overview = "Filme da pasta $folderName.",
                        uriString = file.uri.toString()
                    )
                    mediaDao.insertMedia(movieItem)
                    episodeDao.insertEpisode(
                        EpisodeEntity(
                            mediaId = mediaId,
                            seasonNumber = 0,
                            episodeNumber = 1,
                            title = parsed.title,
                            uriString = file.uri.toString()
                        )
                    )
                    addedMedia.add(movieItem)
                }
            }
        }

        // NÃO criar MediaItemEntity representando a própria pasta (QA-002).
        // Retorna a primeira mídia real escaneada, ou null se a pasta não continha vídeos
        addedMedia.firstOrNull()
    }

    suspend fun addExternalVideo(uri: Uri, displayName: String): MediaItemEntity = withContext(Dispatchers.IO) {
        val existing = mediaDao.getAllMediaList().find { it.uriString == uri.toString() }
        if (existing != null) return@withContext existing

        val parsed = MediaNameParser.parse(displayName)
        val mediaId = UUID.randomUUID().toString()
        val mediaItem = MediaItemEntity(
            id = mediaId,
            title = parsed.title,
            kind = if (parsed.isSeries) MediaKind.SERIES else MediaKind.MOVIE,
            year = parsed.year,
            overview = "Arquivo de vídeo externo ($displayName)",
            uriString = uri.toString()
        )
        mediaDao.insertMedia(mediaItem)
        val ep = EpisodeEntity(
            id = UUID.randomUUID().toString(),
            mediaId = mediaId,
            seasonNumber = if (parsed.isSeries) parsed.seasonNumber else 0,
            episodeNumber = if (parsed.isSeries) parsed.episodeNumber else 1,
            title = parsed.title,
            uriString = uri.toString()
        )
        episodeDao.insertEpisode(ep)
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
            val episodes = com.example.cinelocal.data.torrent.TorrentLaunchHelper.generateSeasonEpisodes(mediaId, title, magnetUri)
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
        // Validação e download PRIMEIRO, antes de qualquer alteração no banco (QA-004)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "CineLocal/1.4.0 (Android)")
        }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw java.io.IOException("Servidor IPTV retornou status HTTP $responseCode")
        }

        val channels = mutableListOf<IptvChannelEntity>()
        var currentName = ""
        var currentGroup = "Geral"
        var currentLogo: String? = null

        connection.inputStream.bufferedReader().use { reader ->
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
        }

        if (channels.isEmpty()) {
            throw IllegalArgumentException("A lista foi baixada, mas nenhum canal válido foi encontrado.")
        }

        // Apenas após validar e confirmar canais legítimos realizamos a substituição (QA-004)
        if (clearExisting) {
            iptvChannelDao.deleteAllChannels()
        }
        iptvChannelDao.insertChannels(channels)
        channels.size
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

    /**
     * Carga inicial da biblioteca caso esteja vazia (QA-001)
     */
    suspend fun loadInitialDataIfEmpty(context: Context? = null) = withContext(Dispatchers.IO) {
        val ctx = context ?: this@MediaRepository.context

        // Re-escaneia diretórios autorizados pelo usuário se banco de mídia estiver zerado
        val mediaList = mediaDao.getAllMediaList()
        if (mediaList.isEmpty() && ctx != null) {
            rescanAll(ctx)
        }

        // Se canais IPTV estiverem vazios, cadastra seleção de canais públicos padrão (IPTV-org)
        val channelCount = iptvChannelDao.getChannelCount()
        if (channelCount == 0) {
            val starterChannels = listOf(
                IptvChannelEntity(
                    name = "TV Brasil",
                    group = "Abertos Brasil",
                    logo = "https://raw.githubusercontent.com/iptv-org/epg/master/logos/TVBrasil.png",
                    url = "https://tvbrasil-stream.ebc.com.br/hls/tvbrasil/index.m3u8"
                ),
                IptvChannelEntity(
                    name = "TV Cultura",
                    group = "Abertos Brasil",
                    logo = "https://raw.githubusercontent.com/iptv-org/epg/master/logos/TVCultura.png",
                    url = "https://cultura.stream.fabricahost.com.br/cultura/index.m3u8"
                ),
                IptvChannelEntity(
                    name = "Rede Minas",
                    group = "Abertos Brasil",
                    logo = "https://raw.githubusercontent.com/iptv-org/epg/master/logos/RedeMinas.png",
                    url = "https://redeminas-live.fabricahost.com.br/redeminas/index.m3u8"
                ),
                IptvChannelEntity(
                    name = "Euronews Português",
                    group = "Notícias",
                    logo = "https://raw.githubusercontent.com/iptv-org/epg/master/logos/EuronewsPortuguese.png",
                    url = "https://rakuten-euronewspt-1-eu.rakuten.wurl.tv/playlist.m3u8"
                ),
                IptvChannelEntity(
                    name = "NASA TV",
                    group = "Documentários",
                    logo = "https://raw.githubusercontent.com/iptv-org/epg/master/logos/NASATVPublic.png",
                    url = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8"
                )
            )
            iptvChannelDao.insertChannels(starterChannels)
        }
    }

    /**
     * Reescaneamento completo da biblioteca (QA-001):
     * 1. Remove itens de pastas fantasmas (/tree/)
     * 2. Re-escaneia pastas autorizadas (SAF persisted permissions)
     * 3. Compara com itens existentes (deduplicação)
     * 4. Remove mídias locais cujos arquivos originais foram excluídos do disco
     */
    suspend fun rescanAll(context: Context? = null): Int = withContext(Dispatchers.IO) {
        val ctx = context ?: this@MediaRepository.context
        var itemsAddedOrReconciled = 0

        // 1. Limpeza de mídias inválidas que representavam pastas puras (QA-002)
        val initialMedia = mediaDao.getAllMediaList()
        for (item in initialMedia) {
            val uriStr = item.uriString ?: ""
            if (uriStr.contains("/tree/") && !uriStr.contains("/document/")) {
                mediaDao.deleteMediaById(item.id)
                episodeDao.deleteEpisodesForMedia(item.id)
            }
        }

        // 2. Re-escanear pastas autorizadas via SAF
        if (ctx != null) {
            val persistedPerms = try {
                ctx.contentResolver.persistedUriPermissions
            } catch (_: Exception) {
                emptyList()
            }

            for (perm in persistedPerms) {
                if (perm.isReadPermission && perm.uri.toString().contains("/tree/")) {
                    try {
                        val scanned = FolderScanner.scanTree(ctx.contentResolver, perm.uri)
                        for (file in scanned) {
                            val parsed = MediaNameParser.parse(file.displayName, file.parentFolder)
                            val fileUriStr = file.uri.toString()

                            val currentMediaList = mediaDao.getAllMediaList()
                            if (parsed.isSeries) {
                                val existingSeries = currentMediaList.find {
                                    it.kind == MediaKind.SERIES && it.title.equals(parsed.title, ignoreCase = true)
                                }
                                val seriesId = existingSeries?.id ?: UUID.randomUUID().toString()
                                if (existingSeries == null) {
                                    mediaDao.insertMedia(
                                        MediaItemEntity(
                                            id = seriesId,
                                            title = parsed.title,
                                            kind = MediaKind.SERIES,
                                            year = parsed.year,
                                            overview = "Série da pasta ${file.parentFolder ?: ""}",
                                            uriString = fileUriStr
                                        )
                                    )
                                }

                                val episodes = episodeDao.getEpisodesForMedia(seriesId).firstOrNull() ?: emptyList()
                                val epExists = episodes.any {
                                    (it.seasonNumber == parsed.seasonNumber && it.episodeNumber == parsed.episodeNumber) ||
                                            it.uriString == fileUriStr
                                }
                                if (!epExists) {
                                    episodeDao.insertEpisode(
                                        EpisodeEntity(
                                            mediaId = seriesId,
                                            seasonNumber = parsed.seasonNumber,
                                            episodeNumber = parsed.episodeNumber,
                                            title = "Episódio ${parsed.episodeNumber} - ${file.displayName.substringBeforeLast('.')}",
                                            uriString = fileUriStr
                                        )
                                    )
                                    itemsAddedOrReconciled++
                                }
                            } else {
                                val movieExists = currentMediaList.any {
                                    it.kind == MediaKind.MOVIE && (it.uriString == fileUriStr || it.title.equals(parsed.title, ignoreCase = true))
                                }
                                if (!movieExists) {
                                    val movieId = UUID.randomUUID().toString()
                                    val movieItem = MediaItemEntity(
                                        id = movieId,
                                        title = parsed.title,
                                        kind = MediaKind.MOVIE,
                                        year = parsed.year,
                                        overview = "Filme local (${file.displayName})",
                                        uriString = fileUriStr
                                    )
                                    mediaDao.insertMedia(movieItem)
                                    episodeDao.insertEpisode(
                                        EpisodeEntity(
                                            mediaId = movieId,
                                            seasonNumber = 0,
                                            episodeNumber = 1,
                                            title = parsed.title,
                                            uriString = fileUriStr
                                        )
                                    )
                                    itemsAddedOrReconciled++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MediaRepository", "Erro ao reescanear pasta: ${perm.uri}", e)
                    }
                }
            }

            // 3. Reconciliar itens locais deletados fora do app
            val mediaAfterScan = mediaDao.getAllMediaList()
            for (media in mediaAfterScan) {
                if (media.kind == MediaKind.MOVIE) {
                    val filePath = media.filePath
                    val uriStr = media.uriString

                    var stillExists = true
                    if (!filePath.isNullOrBlank() && filePath.startsWith("/")) {
                        stillExists = java.io.File(filePath).exists()
                    } else if (!uriStr.isNullOrBlank() && uriStr.startsWith("content://")) {
                        try {
                            val cursor = ctx.contentResolver.query(
                                Uri.parse(uriStr),
                                arrayOf(OpenableColumns.DISPLAY_NAME),
                                null, null, null
                            )
                            stillExists = cursor?.use { it.moveToFirst() } == true
                        } catch (_: Exception) {
                            stillExists = false
                        }
                    }

                    if (!stillExists) {
                        mediaDao.deleteMediaById(media.id)
                        episodeDao.deleteEpisodesForMedia(media.id)
                    }
                }
            }
        }

        itemsAddedOrReconciled
    }
}
