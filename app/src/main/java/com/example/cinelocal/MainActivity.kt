package com.example.cinelocal

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import com.example.cinelocal.data.db.AppDatabase
import com.example.cinelocal.data.model.MediaItemEntity
import com.example.cinelocal.data.model.MediaKind
import com.example.cinelocal.data.repository.MediaRepository
import com.example.cinelocal.player.PlayerViewModel
import com.example.cinelocal.ui.MainViewModel
import com.example.cinelocal.ui.UiEvent
import com.example.cinelocal.ui.components.AddMediaDialog
import com.example.cinelocal.ui.components.CastButton
import com.example.cinelocal.ui.components.CastDeviceDialog
import com.example.cinelocal.ui.components.IptvImportDialog
import com.example.cinelocal.ui.components.MediaDetailSheet
import com.example.cinelocal.ui.components.NativeCastButton
import com.example.cinelocal.ui.components.TmdbConfigDialog
import com.example.cinelocal.ui.screens.ChannelsScreen
import com.example.cinelocal.ui.screens.FavoritesScreen
import com.example.cinelocal.ui.screens.HomeScreen
import com.example.cinelocal.ui.screens.MoviesScreen
import com.example.cinelocal.ui.screens.PlayerScreen
import com.example.cinelocal.ui.screens.SeriesScreen
import com.example.cinelocal.ui.screens.SettingsScreen
import com.example.cinelocal.ui.theme.CineLocalTheme
import com.example.cinelocal.ui.theme.CineRed
import com.example.cinelocal.ui.theme.DarkBackground
import com.example.cinelocal.ui.theme.DarkSurface
import com.example.cinelocal.ui.theme.DarkSurfaceVariant
import com.example.cinelocal.ui.theme.TextPrimary
import com.example.cinelocal.ui.theme.TextSecondary

enum class AppTab(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Default.Home),
    MOVIES("Filmes", Icons.Default.Movie),
    SERIES("Séries", Icons.Default.Tv),
    CHANNELS("Ao Vivo", Icons.Default.Tv),
    FAVORITES("Favoritos", Icons.Default.Favorite),
    SETTINGS("Config", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var db: AppDatabase
    private lateinit var repository: MediaRepository
    private lateinit var mainViewModel: MainViewModel
    private lateinit var playerViewModel: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "cinelocal.db"
        ).fallbackToDestructiveMigration().build()

        repository = MediaRepository(applicationContext, db)
        mainViewModel = MainViewModel(application, repository)
        playerViewModel = PlayerViewModel(application, repository)

        setContent {
            CineLocalTheme {
                CineLocalApp(
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerViewModel.releasePlayer()
    }
}

@Composable
fun CineLocalApp(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current

    var activeTab by remember { mutableStateOf(AppTab.HOME) }
    var isPlayerActive by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Dialogs state
    var showAddDialog by remember { mutableStateOf(false) }
    var showIptvDialog by remember { mutableStateOf(false) }
    var showTmdbDialog by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }

    // Cast state
    val castState by playerViewModel.castState.collectAsStateWithLifecycle()

    // Storage Access Framework Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if not supported
            }
            mainViewModel.addFolderByUri(uri, null)
        }
    }

    // View Model Observables
    val allMedia by mainViewModel.allMedia.collectAsStateWithLifecycle()
    val movies by mainViewModel.movies.collectAsStateWithLifecycle()
    val series by mainViewModel.series.collectAsStateWithLifecycle()
    val favorites by mainViewModel.favorites.collectAsStateWithLifecycle()
    val continueWatching by mainViewModel.continueWatching.collectAsStateWithLifecycle()
    val allMediaWithEpisodes by mainViewModel.allMediaWithEpisodes.collectAsStateWithLifecycle()

    val iptvGroups by mainViewModel.iptvGroups.collectAsStateWithLifecycle()
    val iptvChannels by mainViewModel.iptvChannels.collectAsStateWithLifecycle()
    val favoriteChannels by mainViewModel.favoriteChannels.collectAsStateWithLifecycle()
    val selectedIptvGroup by mainViewModel.selectedIptvGroup.collectAsStateWithLifecycle()

    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedMediaWithEpisodes by mainViewModel.selectedMediaWithEpisodes.collectAsStateWithLifecycle()
    val isLoading by mainViewModel.isLoading.collectAsStateWithLifecycle()
    val tmdbApiKey by mainViewModel.tmdbApiKey.collectAsStateWithLifecycle()

    // Handle One-shot UI Events
    LaunchedEffect(Unit) {
        mainViewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.OpenPlayer -> {
                    playerViewModel.playMediaEpisode(
                        episode = event.episode,
                        mediaTitle = event.mediaTitle,
                        allEpisodes = event.allEpisodes
                    )
                    isPlayerActive = true
                }
                is UiEvent.OpenLiveStream -> {
                    playerViewModel.playLiveStream(
                        title = event.channel.name,
                        group = event.channel.group,
                        streamUrl = event.channel.url
                    )
                    isPlayerActive = true
                }
            }
        }
    }

    if (isPlayerActive) {
        PlayerScreen(
            playerViewModel = playerViewModel,
            onBackClick = {
                playerViewModel.player.pause()
                isPlayerActive = false
            }
        )
    } else {
        Scaffold(
            containerColor = DarkBackground,
            topBar = {
                // Top App Bar
                Surface(
                    color = DarkBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (!isSearchExpanded) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "CINELOCAL",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = CineRed,
                                        letterSpacing = 1.5.sp,
                                        fontSize = 20.sp
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    NativeCastButton(castState = castState)

                                    CastButton(
                                        castState = castState,
                                        onClick = {
                                            playerViewModel.castManager.startDiscovery()
                                            showCastDialog = true
                                        }
                                    )

                                    IconButton(
                                        onClick = { isSearchExpanded = true },
                                        modifier = Modifier.testTag("search_toggle_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Buscar",
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(
                                        onClick = { showAddDialog = true },
                                        modifier = Modifier.testTag("add_media_top_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Adicionar Mídia",
                                            tint = Color.White
                                        )
                                    }

                                    IconButton(
                                        onClick = { mainViewModel.rescanAll() },
                                        modifier = Modifier.testTag("rescan_top_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Atualizar",
                                            tint = Color.White
                                        )
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { mainViewModel.setSearchQuery(it) },
                                    placeholder = { Text("Buscar filmes, séries, canais…", fontSize = 13.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("search_text_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CineRed,
                                        unfocusedBorderColor = DarkSurfaceVariant,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    ),
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            mainViewModel.setSearchQuery("")
                                            isSearchExpanded = false
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Fechar busca",
                                                tint = TextSecondary
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    AppTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CineRed,
                                selectedTextColor = CineRed,
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                if (activeTab == AppTab.HOME || activeTab == AppTab.MOVIES || activeTab == AppTab.SERIES) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = CineRed,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("fab_add_media")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Adicionar")
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Loading Spinner
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CineRed)
                    }
                }

                when (activeTab) {
                    AppTab.HOME -> {
                        HomeScreen(
                            allMedia = allMedia,
                            movies = movies,
                            series = series,
                            continueWatching = continueWatching,
                            allMediaWithEpisodes = allMediaWithEpisodes,
                            channels = iptvChannels,
                            onMediaClick = { media -> mainViewModel.selectMedia(media) },
                            onPlayMedia = { media ->
                                val mediaWithEps = allMediaWithEpisodes.find { it.media.id == media.id }
                                val firstEp = mediaWithEps?.episodes?.firstOrNull()
                                if (firstEp != null) {
                                    playerViewModel.playMediaEpisode(
                                        episode = firstEp,
                                        mediaTitle = media.title,
                                        allEpisodes = mediaWithEps.episodes
                                    )
                                    isPlayerActive = true
                                }
                            },
                            onPlayEpisode = { ep, eps, mediaTitle ->
                                playerViewModel.playMediaEpisode(
                                    episode = ep,
                                    mediaTitle = mediaTitle,
                                    allEpisodes = eps
                                )
                                isPlayerActive = true
                            },
                            onPlayChannel = { channel ->
                                playerViewModel.playLiveStream(
                                    title = channel.name,
                                    group = channel.group,
                                    streamUrl = channel.url
                                )
                                isPlayerActive = true
                            },
                            onFavoriteToggle = { media -> mainViewModel.toggleMediaFavorite(media) },
                            onChannelFavoriteToggle = { channel -> mainViewModel.toggleChannelFavorite(channel) },
                            onAddMediaClick = { showAddDialog = true },
                            onNavigateToMovies = { activeTab = AppTab.MOVIES },
                            onNavigateToSeries = { activeTab = AppTab.SERIES },
                            onNavigateToChannels = { activeTab = AppTab.CHANNELS }
                        )
                    }
                    AppTab.MOVIES -> {
                        MoviesScreen(
                            movies = movies,
                            onMovieClick = { media -> mainViewModel.selectMedia(media) },
                            onFavoriteToggle = { media -> mainViewModel.toggleMediaFavorite(media) }
                        )
                    }
                    AppTab.SERIES -> {
                        SeriesScreen(
                            series = series,
                            onSeriesClick = { media -> mainViewModel.selectMedia(media) },
                            onFavoriteToggle = { media -> mainViewModel.toggleMediaFavorite(media) }
                        )
                    }
                    AppTab.CHANNELS -> {
                        ChannelsScreen(
                            channels = iptvChannels,
                            groups = iptvGroups,
                            selectedGroup = selectedIptvGroup,
                            onSelectGroup = { group -> mainViewModel.selectIptvGroup(group) },
                            onPlayChannel = { channel ->
                                playerViewModel.playLiveStream(
                                    title = channel.name,
                                    group = channel.group,
                                    streamUrl = channel.url
                                )
                                isPlayerActive = true
                            },
                            onFavoriteToggle = { channel -> mainViewModel.toggleChannelFavorite(channel) },
                            onImportClick = { showIptvDialog = true }
                        )
                    }
                    AppTab.FAVORITES -> {
                        FavoritesScreen(
                            favoriteMedia = favorites,
                            favoriteChannels = favoriteChannels,
                            onMediaClick = { media -> mainViewModel.selectMedia(media) },
                            onFavoriteMediaToggle = { media -> mainViewModel.toggleMediaFavorite(media) },
                            onPlayChannel = { channel ->
                                playerViewModel.playLiveStream(
                                    title = channel.name,
                                    group = channel.group,
                                    streamUrl = channel.url
                                )
                                isPlayerActive = true
                            },
                            onChannelFavoriteToggle = { channel -> mainViewModel.toggleChannelFavorite(channel) }
                        )
                    }
                    AppTab.SETTINGS -> {
                        SettingsScreen(
                            tmdbApiKey = tmdbApiKey,
                            totalMediaCount = allMedia.size,
                            totalChannelCount = iptvChannels.size,
                            onOpenTmdbConfig = { showTmdbDialog = true },
                            onOpenIptvManager = { showIptvDialog = true },
                            onRescanLibrary = { mainViewModel.rescanAll() }
                        )
                    }
                }
            }
        }

        // Details Bottom Sheet
        selectedMediaWithEpisodes?.let { mediaWithEps ->
            MediaDetailSheet(
                mediaWithEpisodes = mediaWithEps,
                onDismiss = { mainViewModel.clearSelectedMedia() },
                onPlayEpisode = { ep, eps ->
                    playerViewModel.playMediaEpisode(
                        episode = ep,
                        mediaTitle = mediaWithEps.media.title,
                        allEpisodes = eps
                    )
                    mainViewModel.clearSelectedMedia()
                    isPlayerActive = true
                },
                onFavoriteToggle = { mainViewModel.toggleMediaFavorite(mediaWithEps.media) },
                onDeleteClick = { mainViewModel.deleteMedia(mediaWithEps.media.id) }
            )
        }

        // Dialogs
        if (showAddDialog) {
            AddMediaDialog(
                onDismiss = { showAddDialog = false },
                onPickFolderClick = { folderPickerLauncher.launch(null) },
                onAddDirectStream = { title, url, isSeries ->
                    mainViewModel.addDirectMedia(title, url, isSeries)
                }
            )
        }

        if (showIptvDialog) {
            IptvImportDialog(
                onDismiss = { showIptvDialog = false },
                onImportUrl = { url, clearExisting ->
                    mainViewModel.importIptvPlaylist(url, clearExisting)
                },
                onAddSingleChannel = { name, url, group ->
                    mainViewModel.addCustomChannel(name, url, group)
                }
            )
        }

        if (showTmdbDialog) {
            TmdbConfigDialog(
                initialKey = tmdbApiKey,
                onDismiss = { showTmdbDialog = false },
                onSaveKey = { key -> mainViewModel.saveTmdbApiKey(key) }
            )
        }

        if (showCastDialog) {
            CastDeviceDialog(
                castState = castState,
                onSelectDevice = { routeId ->
                    playerViewModel.castManager.selectDevice(routeId)
                    playerViewModel.triggerCastForCurrentMedia()
                },
                onDisconnect = { playerViewModel.castManager.disconnect() },
                onDismiss = { showCastDialog = false }
            )
        }
    }
}
