// Location: app/src/main/java/com/example/melody/MainActivity.kt
package com.example.melody

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.melody.data.Song
import com.example.melody.data.MusicRepository
import com.example.melody.data.api.BackendSong
import com.example.melody.service.MusicPlayerService
import com.example.melody.ui.theme.MelodyTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var musicService: MusicPlayerService? = null
    private var serviceBound by mutableStateOf(false)
    private var musicRepository: MusicRepository? = null
    private var localSongs by mutableStateOf<List<Song>>(emptyList())
    private var backendSongs by mutableStateOf<List<BackendSong>>(emptyList())
    private var isLoading by mutableStateOf(false)
    private var isPlaying by mutableStateOf(false)
    private var currentSong by mutableStateOf<Song?>(null)
    private var selectedTab by mutableStateOf(0)
    private var searchQuery by mutableStateOf("")
    private var searchResults by mutableStateOf<List<BackendSong>>(emptyList())
    private var popularArtists by mutableStateOf<List<String>>(emptyList())
    private var recentSongs by mutableStateOf<List<BackendSong>>(emptyList())

    companion object {
        private const val TAG = "MainActivity"
    }

    // Service connection
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicPlayerService.MusicPlayerBinder
            musicService = binder.getService()
            serviceBound = true
            Log.d(TAG, "Service connected")
            updatePlayerState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceBound = false
            musicService = null
            Log.d(TAG, "Service disconnected")
        }
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readExternalStorage = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
        val readMediaAudio = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] ?: false
        } else true

        if (readExternalStorage || readMediaAudio) {
            loadSongs()
        } else {
            Toast.makeText(this, "Permission required to access music files", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        musicRepository = MusicRepository(this)

        // Observe local songs from database
        musicRepository?.let { repository ->
            lifecycleScope.launch {
                repository.allSongs.collect { songList ->
                    localSongs = songList
                    Log.d(TAG, "Local songs updated: ${songList.size}")
                }
            }

            // Observe backend songs
            lifecycleScope.launch {
                repository.backendSongs.collect { songList ->
                    backendSongs = songList
                    Log.d(TAG, "Backend songs updated: ${songList.size}")
                }
            }

            // Observe loading state
            lifecycleScope.launch {
                repository.isLoading.collect { loading ->
                    isLoading = loading
                }
            }
        }

        setContent {
            MelodyTheme {
                MelodyApp(
                    localSongs = localSongs,
                    backendSongs = backendSongs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    selectedTab = selectedTab,
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    popularArtists = popularArtists,
                    recentSongs = recentSongs,
                    isLoading = isLoading,
                    onSongClick = { song -> playSong(song) },
                    onBackendSongClick = { backendSong -> playBackendSong(backendSong) },
                    onPlayPauseClick = { togglePlayPause() },
                    onPreviousClick = { playPreviousSong() },
                    onNextClick = { playNextSong() },
                    onTabSelected = { tab -> selectedTab = tab },
                    onSearchQueryChange = { query ->
                        searchQuery = query
                        performSearch(query)
                    },
                    onRefresh = { loadBackendData() }
                )
            }
        }

        // Check and request permissions
        checkPermissions()

        // Bind to service
        bindMusicService()

        // Load backend data
        loadBackendData()
    }

    private fun loadBackendData() {
        musicRepository?.let { repository ->
            lifecycleScope.launch {
                // Load popular artists
                popularArtists = repository.getPopularArtists()

                // Load recent songs
                recentSongs = repository.getRecentSongs()
            }
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            searchResults = emptyList()
            return
        }

        musicRepository?.let { repository ->
            lifecycleScope.launch {
                searchResults = repository.searchSongs(query)
            }
        }
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check for storage permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            loadSongs()
        }
    }

    private fun loadSongs() {
        musicRepository?.let { repository ->
            // Scan the device for music (this will update the database)
            repository.scanDeviceForMusic()
            Log.d(TAG, "Started scanning for music files")
        }
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicPlayerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun playSong(song: Song) {
        Log.d(TAG, "playSong called with: ${song.title}")

        // Set current song immediately for UI responsiveness
        currentSong = song
        isPlaying = true

        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = MusicPlayerService.ACTION_PLAY_SONG
            putExtra(MusicPlayerService.EXTRA_SONG_ID, song.id)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // Update state after a short delay to get service state
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500) // Wait for service to process
            updatePlayerState()
        }

        Log.d(TAG, "Playing song: ${song.title}, currentSong set: ${currentSong?.title}")
    }

    private fun playBackendSong(backendSong: BackendSong) {
        Log.d(TAG, "playBackendSong called with: ${backendSong.title}")

        // Convert backend song to local song format
        val song = musicRepository?.backendSongToLocal(backendSong)
        if (song != null) {
            playSong(song)
        }
    }

    private fun togglePlayPause() {
        Log.d(TAG, "togglePlayPause called, serviceBound: $serviceBound")

        musicService?.let { service ->
            val intent = Intent(this, MusicPlayerService::class.java).apply {
                action = if (service.isPlaying()) {
                    MusicPlayerService.ACTION_PAUSE
                } else {
                    MusicPlayerService.ACTION_RESUME
                }
            }
            startService(intent)

            // Update state immediately for UI responsiveness
            isPlaying = !isPlaying

            // Then update from service
            lifecycleScope.launch {
                kotlinx.coroutines.delay(100)
                updatePlayerState()
            }
        } ?: run {
            Log.w(TAG, "Music service not connected")
            Toast.makeText(this, "Music service not connected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playNextSong() {
        Log.d(TAG, "playNextSong called, currentSong: ${currentSong?.title}")

        currentSong?.let { current ->
            // Try to find next song in current list (local or backend)
            val allCurrentSongs = when (selectedTab) {
                0 -> backendSongs.map { musicRepository?.backendSongToLocal(it) }.filterNotNull()
                1 -> localSongs
                else -> localSongs + backendSongs.map { musicRepository?.backendSongToLocal(it) }.filterNotNull()
            }

            val currentIndex = allCurrentSongs.indexOfFirst { it.id == current.id }
            if (currentIndex != -1 && currentIndex < allCurrentSongs.size - 1) {
                playSong(allCurrentSongs[currentIndex + 1])
            } else if (allCurrentSongs.isNotEmpty()) {
                // Loop back to first song
                playSong(allCurrentSongs[0])
            }
        } ?: run {
            // If no current song, play the first available song
            val firstSong = when (selectedTab) {
                0 -> backendSongs.firstOrNull()?.let { musicRepository?.backendSongToLocal(it) }
                1 -> localSongs.firstOrNull()
                else -> localSongs.firstOrNull() ?: backendSongs.firstOrNull()?.let { musicRepository?.backendSongToLocal(it) }
            }
            firstSong?.let { playSong(it) }
        }
    }

    private fun playPreviousSong() {
        Log.d(TAG, "playPreviousSong called, currentSong: ${currentSong?.title}")

        currentSong?.let { current ->
            // Try to find previous song in current list (local or backend)
            val allCurrentSongs = when (selectedTab) {
                0 -> backendSongs.map { musicRepository?.backendSongToLocal(it) }.filterNotNull()
                1 -> localSongs
                else -> localSongs + backendSongs.map { musicRepository?.backendSongToLocal(it) }.filterNotNull()
            }

            val currentIndex = allCurrentSongs.indexOfFirst { it.id == current.id }
            if (currentIndex > 0) {
                playSong(allCurrentSongs[currentIndex - 1])
            } else if (allCurrentSongs.isNotEmpty()) {
                // Loop to last song
                playSong(allCurrentSongs[allCurrentSongs.size - 1])
            }
        } ?: run {
            // If no current song, play the last available song
            val lastSong = when (selectedTab) {
                0 -> backendSongs.lastOrNull()?.let { musicRepository?.backendSongToLocal(it) }
                1 -> localSongs.lastOrNull()
                else -> localSongs.lastOrNull() ?: backendSongs.lastOrNull()?.let { musicRepository?.backendSongToLocal(it) }
            }
            lastSong?.let { playSong(it) }
        }
    }

    private fun updatePlayerState() {
        musicService?.let { service ->
            val wasPlaying = isPlaying
            val wasCurrent = currentSong

            isPlaying = service.isPlaying()
            val serviceSong = service.getCurrentSong()

            // Only update currentSong if service has a song
            if (serviceSong != null) {
                currentSong = serviceSong
            }

            Log.d(TAG, "updatePlayerState - isPlaying: $isPlaying, currentSong: ${currentSong?.title}")
            Log.d(TAG, "State changed - playing: $wasPlaying -> $isPlaying, song: ${wasCurrent?.title} -> ${currentSong?.title}")
        } ?: run {
            Log.d(TAG, "updatePlayerState - service is null")
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - updating player state")
        updatePlayerState()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MelodyApp(
    localSongs: List<Song>,
    backendSongs: List<BackendSong>,
    currentSong: Song?,
    isPlaying: Boolean,
    selectedTab: Int,
    searchQuery: String,
    searchResults: List<BackendSong>,
    popularArtists: List<String>,
    recentSongs: List<BackendSong>,
    isLoading: Boolean,
    onSongClick: (Song) -> Unit,
    onBackendSongClick: (BackendSong) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRefresh: () -> Unit
) {
    // Debug: Log current state
    LaunchedEffect(currentSong, isPlaying) {
        Log.d("MelodyApp", "Recomposition - currentSong: ${currentSong?.title}, isPlaying: $isPlaying")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            0 -> "Explore"
                            1 -> "Your Library"
                            2 -> "Search"
                            3 -> "Profile"
                            else -> "Melody"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        bottomBar = {
            Column {
                // Music Player above navigation
                currentSong?.let { song ->
                    Log.d("MelodyApp", "Rendering BottomMusicPlayer for: ${song.title}")
                    BottomMusicPlayer(
                        song = song,
                        isPlaying = isPlaying,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        modifier = Modifier.fillMaxWidth()
                    )
                } ?: run {
                    Log.d("MelodyApp", "currentSong is null - not rendering BottomMusicPlayer")
                }

                // Navigation Bar
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                        label = { Text("Explore") },
                        selected = selectedTab == 0,
                        onClick = { onTabSelected(0) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Library") },
                        label = { Text("Library") },
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        label = { Text("Search") },
                        selected = selectedTab == 2,
                        onClick = { onTabSelected(2) }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                        label = { Text("Profile") },
                        selected = selectedTab == 3,
                        onClick = { onTabSelected(3) }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> ExploreContent(
                    backendSongs = backendSongs,
                    recentSongs = recentSongs,
                    popularArtists = popularArtists,
                    isLoading = isLoading,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onBackendSongClick = onBackendSongClick,
                    bottomPadding = if (currentSong != null) 80.dp else 16.dp
                )
                1 -> LibraryContent(
                    songs = localSongs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onSongClick = onSongClick,
                    bottomPadding = if (currentSong != null) 80.dp else 16.dp
                )
                2 -> SearchContent(
                    searchQuery = searchQuery,
                    searchResults = searchResults,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onSearchQueryChange = onSearchQueryChange,
                    onBackendSongClick = onBackendSongClick,
                    bottomPadding = if (currentSong != null) 80.dp else 16.dp
                )
                3 -> ProfileContent()
            }
        }
    }
}

@Composable
fun ExploreContent(
    backendSongs: List<BackendSong>,
    recentSongs: List<BackendSong>,
    popularArtists: List<String>,
    isLoading: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    onBackendSongClick: (BackendSong) -> Unit,
    bottomPadding: Dp
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Discover Music from Cloud",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Loading indicator
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Recent Songs Section
        if (recentSongs.isNotEmpty()) {
            item {
                Text(
                    text = "Recently Added",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(recentSongs.take(5)) { song ->
                BackendSongItem(
                    song = song,
                    isCurrentSong = song.id == currentSong?.id,
                    isPlaying = isPlaying && song.id == currentSong?.id,
                    onClick = {
                        Log.d("ExploreContent", "Recent song clicked: ${song.title}")
                        onBackendSongClick(song)
                    }
                )
            }
        }

        // Popular Artists Section
        if (popularArtists.isNotEmpty()) {
            item {
                Text(
                    text = "Popular Artists",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(popularArtists.take(10)) { artist ->
                        Card(
                            modifier = Modifier
                                .width(120.dp)
                                .clickable { /* TODO: Navigate to artist */ },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // All Songs Section
        if (backendSongs.isNotEmpty()) {
            item {
                Text(
                    text = "All Songs (${backendSongs.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            items(backendSongs) { song ->
                BackendSongItem(
                    song = song,
                    isCurrentSong = song.id == currentSong?.id,
                    isPlaying = isPlaying && song.id == currentSong?.id,
                    onClick = {
                        Log.d("ExploreContent", "Song clicked: ${song.title}")
                        onBackendSongClick(song)
                    }
                )
            }
        }

        // Empty state
        if (!isLoading && backendSongs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No songs available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Check your internet connection or try refreshing",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryContent(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    bottomPadding: Dp
) {
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Your library is empty",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Add some music to your device to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = bottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Local Music (${songs.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            items(songs) { song ->
                SongItem(
                    song = song,
                    isCurrentSong = song.id == currentSong?.id,
                    isPlaying = isPlaying && song.id == currentSong?.id,
                    onClick = {
                        Log.d("LibraryContent", "Song clicked: ${song.title}")
                        onSongClick(song)
                    }
                )
            }
        }
    }
}

@Composable
fun SearchContent(
    searchQuery: String,
    searchResults: List<BackendSong>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onBackendSongClick: (BackendSong) -> Unit,
    bottomPadding: Dp
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = bottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                label = { Text("Search for songs, artists, albums...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        if (searchQuery.isNotBlank()) {
            item {
                Text(
                    text = "Search Results (${searchResults.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (searchResults.isNotEmpty()) {
                items(searchResults) { song ->
                    BackendSongItem(
                        song = song,
                        isCurrentSong = song.id == currentSong?.id,
                        isPlaying = isPlaying && song.id == currentSong?.id,
                        onClick = {
                            Log.d("SearchContent", "Search result clicked: ${song.title}")
                            onBackendSongClick(song)
                        }
                    )
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "No results found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Try a different search term",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Start typing to search for music",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(
                    text = "Music Lover",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Listening since today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        // Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "0",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "0h",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Listened",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Settings
        Text(
            text = "Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                SettingsItem("🎵", "Audio Quality", "High (320kbps)")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem("🌙", "Theme", "System default")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem("📱", "Storage", "Manage downloads")
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem("☁️", "Sync", "Upload local music")
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: String,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(end = 16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun SongItem(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentSong) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentSong) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song artwork placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCurrentSong) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentSong && isPlaying) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Song",
                        tint = if (isCurrentSong) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (song.album.isNotBlank()) {
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Duration
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // More options
            IconButton(onClick = { /* TODO: Show options menu */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun BackendSongItem(
    song: BackendSong,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentSong) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isCurrentSong) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song artwork placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isCurrentSong) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentSong && isPlaying) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Row {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Cloud song",
                            tint = if (isCurrentSong) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrentSong) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (song.album.isNotBlank()) {
                    Text(
                        text = song.album,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Cloud icon to indicate it's from backend
            Icon(
                imageVector = Icons.Default.Cloud,
                contentDescription = "Cloud song",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // More options
            IconButton(onClick = { /* TODO: Show options menu */ }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun BottomMusicPlayer(
    song: Song,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song artwork
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                    contentDescription = "Now playing",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Control buttons
            IconButton(
                onClick = onPreviousClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onNextClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// Utility function to format duration
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Preview(showBackground = true)
@Composable
fun MelodyAppPreview() {
    MelodyTheme {
        MelodyApp(
            localSongs = emptyList(),
            backendSongs = emptyList(),
            currentSong = null,
            isPlaying = false,
            selectedTab = 0,
            searchQuery = "",
            searchResults = emptyList(),
            popularArtists = emptyList(),
            recentSongs = emptyList(),
            isLoading = false,
            onSongClick = { },
            onBackendSongClick = { },
            onPlayPauseClick = { },
            onPreviousClick = { },
            onNextClick = { },
            onTabSelected = { },
            onSearchQueryChange = { },
            onRefresh = { }
        )
    }
}
