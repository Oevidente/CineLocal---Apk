package com.example.cinelocal.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cinelocal.data.db.AppDatabase
import com.example.cinelocal.data.model.EpisodeEntity
import com.example.cinelocal.data.model.IptvChannelEntity
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.MediaKind
import com.example.cinelocal.data.model.MediaWithEpisodes
import com.example.cinelocal.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class OpenPlayer(
        val episode: EpisodeEntity,
        val mediaTitle: String,
        val allEpisodes: List<EpisodeEntity>
    ) : UiEvent()
    data class OpenLiveStream(
        val channel: IptvChannelEntity
    ) : UiEvent()
}

class MainViewModel(
    application: Application,
    val repository: MediaRepository
) : AndroidViewModel(application) {

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // Search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected IPTV group/category
    private val _selectedIptvGroup = MutableStateFlow("Todos")
    val selectedIptvGroup: StateFlow<String> = _selectedIptvGroup.asStateFlow()

    // Selected Media Item for details dialog
    private val _selectedMediaWithEpisodes = MutableStateFlow<MediaWithEpisodes?>(null)
    val selectedMediaWithEpisodes: StateFlow<MediaWithEpisodes?> = _selectedMediaWithEpisodes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // TMDb Key setting
    private val _tmdbApiKey = MutableStateFlow("")
    val tmdbApiKey: StateFlow<String> = _tmdbApiKey.asStateFlow()

    // OpenSubtitles settings
    private val _openSubtitlesApiKey = MutableStateFlow("")
    val openSubtitlesApiKey: StateFlow<String> = _openSubtitlesApiKey.asStateFlow()

    private val _openSubtitlesUsername = MutableStateFlow("")
    val openSubtitlesUsername: StateFlow<String> = _openSubtitlesUsername.asStateFlow()

    private val _openSubtitlesPassword = MutableStateFlow("")
    val openSubtitlesPassword: StateFlow<String> = _openSubtitlesPassword.asStateFlow()

    init {
        viewModelScope.launch {
            repository.loadInitialDataIfEmpty()
            val tmdbKey = repository.getSetting("tmdb_api_key").firstOrNull() ?: ""
            _tmdbApiKey.value = tmdbKey
            _openSubtitlesApiKey.value = repository.getSetting("opensubtitles_api_key").firstOrNull() ?: ""
            _openSubtitlesUsername.value = repository.getSetting("opensubtitles_username").firstOrNull() ?: ""
            _openSubtitlesPassword.value = repository.getSetting("opensubtitles_password").firstOrNull() ?: ""
        }
    }

    // Media Streams
    val allMedia: StateFlow<List<MediaItemEntity>> = repository.allMedia
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.originalTitle?.contains(query, ignoreCase = true) == true ||
                        it.genres?.contains(query, ignoreCase = true) == true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val movies: StateFlow<List<MediaItemEntity>> = repository.movies
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter { it.title.contains(query, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val series: StateFlow<List<MediaItemEntity>> = repository.series
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter { it.title.contains(query, ignoreCase = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val torrents: StateFlow<List<MediaItemEntity>> = repository.torrents
        .combine(_searchQuery) { list, query ->
            if (query.isBlank()) list
            else list.filter { it.title.contains(query, ignoreCase = true) || it.overview?.contains(query, ignoreCase = true) == true }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<MediaItemEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<EpisodeEntity>> = repository.continueWatchingEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMediaWithEpisodes: StateFlow<List<MediaWithEpisodes>> = repository.allMediaWithEpisodes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // IPTV Streams
    val iptvGroups: StateFlow<List<String>> = repository.channelGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Todos"))

    val iptvChannels: StateFlow<List<IptvChannelEntity>> = combine(
        repository.allChannels,
        _selectedIptvGroup,
        _searchQuery
    ) { channels, group, query ->
        channels.filter { channel ->
            val matchGroup = (group == "Todos" || channel.group.equals(group, ignoreCase = true))
            val matchQuery = (query.isBlank() || channel.name.contains(query, ignoreCase = true) || channel.group.contains(query, ignoreCase = true))
            matchGroup && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteChannels: StateFlow<List<IptvChannelEntity>> = repository.favoriteChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedNetworkServers: StateFlow<List<com.example.cinelocal.data.model.NetworkServerEntity>> = repository.allNetworkServers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showSmbExplorer = MutableStateFlow(false)
    val showSmbExplorer: StateFlow<Boolean> = _showSmbExplorer.asStateFlow()

    fun openSmbExplorer() {
        _showSmbExplorer.value = true
    }

    fun closeSmbExplorer() {
        _showSmbExplorer.value = false
    }

    fun saveNetworkServer(server: com.example.cinelocal.data.model.NetworkServerEntity) {
        viewModelScope.launch {
            repository.saveNetworkServer(server)
            _uiEvents.emit(UiEvent.ShowToast("Computador '${server.name}' salvo!"))
        }
    }

    fun deleteNetworkServer(server: com.example.cinelocal.data.model.NetworkServerEntity) {
        viewModelScope.launch {
            repository.deleteNetworkServer(server)
            _uiEvents.emit(UiEvent.ShowToast("Computador removido"))
        }
    }

    fun importSmbFolder(
        config: com.example.cinelocal.data.smb.SmbConnectionConfig,
        shareName: String,
        dirPath: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _uiEvents.emit(UiEvent.ShowToast("Escaneando pasta remota no computador..."))
                val count = repository.importSmbFolder(config, shareName, dirPath)
                _uiEvents.emit(UiEvent.ShowToast("$count título(s) importado(s) do computador para a biblioteca!"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Erro ao importar do computador: ${e.localizedMessage}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectIptvGroup(group: String) {
        _selectedIptvGroup.value = group
    }

    fun selectMedia(mediaItem: MediaItemEntity) {
        viewModelScope.launch {
            repository.getMediaWithEpisodes(mediaItem.id).collect {
                _selectedMediaWithEpisodes.value = it
            }
        }
    }

    fun clearSelectedMedia() {
        _selectedMediaWithEpisodes.value = null
    }

    fun toggleMediaFavorite(media: MediaItemEntity) {
        viewModelScope.launch {
            repository.toggleMediaFavorite(media.id, !media.isFavorite)
        }
    }

    fun toggleChannelFavorite(channel: IptvChannelEntity) {
        viewModelScope.launch {
            repository.toggleChannelFavorite(channel.id, !channel.isFavorite)
        }
    }

    fun deleteMedia(mediaId: String) {
        viewModelScope.launch {
            repository.deleteMedia(mediaId)
            _selectedMediaWithEpisodes.value = null
            _uiEvents.emit(UiEvent.ShowToast("Item removido da biblioteca"))
        }
    }

    fun addFolderByUri(uri: Uri, customTitle: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val media = repository.addFolderByUri(uri, customTitle)
                _uiEvents.emit(UiEvent.ShowToast("Pasta adicionada: ${media.title}"))
                selectMedia(media)
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Erro: ${e.localizedMessage}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addStandaloneVideoFiles(uris: List<Uri>, onFirstItemReady: ((MediaItemEntity) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val added = repository.addStandaloneVideoFiles(uris, getApplication())
                val count = added.size
                _uiEvents.emit(UiEvent.ShowToast("$count vídeo(s) adicionado(s) à biblioteca!"))
                added.firstOrNull()?.let { first ->
                    selectMedia(first)
                    onFirstItemReady?.invoke(first)
                }
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Erro ao adicionar vídeos: ${e.localizedMessage}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addDirectMedia(title: String, url: String, isSeries: Boolean) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.addDirectStream(
                    title = title,
                    url = url,
                    kind = if (isSeries) MediaKind.SERIES else MediaKind.MOVIE
                )
                _uiEvents.emit(UiEvent.ShowToast("Mídia adicionada com sucesso!"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Erro: ${e.localizedMessage}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addTorrentMedia(magnetUri: String, customTitle: String? = null, isSeries: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.addTorrentMedia(magnetUri, customTitle, isSeries)
                _uiEvents.emit(UiEvent.ShowToast("Torrent Magnet adicionado à biblioteca!"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Erro ao adicionar Magnet: ${e.localizedMessage}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importIptvPlaylist(url: String, clearExisting: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val count = repository.importIptvFromUrl(url, clearExisting)
                _uiEvents.emit(UiEvent.ShowToast("$count canais importados com sucesso!"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Erro ao importar lista: ${e.localizedMessage}"))
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addCustomChannel(name: String, url: String, group: String) {
        viewModelScope.launch {
            try {
                repository.addSingleIptvChannel(name, url, group)
                _uiEvents.emit(UiEvent.ShowToast("Canal '$name' adicionado!"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Erro: ${e.localizedMessage}"))
            }
        }
    }

    fun saveTmdbApiKey(key: String) {
        viewModelScope.launch {
            repository.setSetting("tmdb_api_key", key)
            _tmdbApiKey.value = key
            _uiEvents.emit(UiEvent.ShowToast("Chave TMDb salva com sucesso!"))
        }
    }

    fun saveOpenSubtitlesConfig(key: String, username: String, pass: String) {
        viewModelScope.launch {
            repository.setSetting("opensubtitles_api_key", key)
            repository.setSetting("opensubtitles_username", username)
            repository.setSetting("opensubtitles_password", pass)
            _openSubtitlesApiKey.value = key
            _openSubtitlesUsername.value = username
            _openSubtitlesPassword.value = pass
            _uiEvents.emit(UiEvent.ShowToast("Credenciais OpenSubtitles salvas com sucesso!"))
        }
    }

    fun rescanAll() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.rescanAll()
                _uiEvents.emit(UiEvent.ShowToast("Biblioteca atualizada!"))
            } catch (e: Exception) {
                _uiEvents.emit(UiEvent.ShowToast("Erro ao atualizar: ${e.localizedMessage}"))
            } finally {
                _isLoading.value = false
            }
        }
    }
}
