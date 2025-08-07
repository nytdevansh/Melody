// Location: app/src/main/java/com/example/melody/data/MusicRepository.kt
package com.example.melody.data

import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.melody.data.api.BackendSong
import com.example.melody.data.api.UploadRequest
import com.example.melody.data.database.MusicDatabase
import com.example.melody.network.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.math.BigInteger

fun ByteArray.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    return BigInteger(1, md.digest(this)).toString(16).padStart(64, '0')
}

class MusicRepository(private val context: Context) {

    private val apiService = NetworkModule.apiService
    private val database = MusicDatabase.getDatabase(context)
    private val songDao = database.songDao()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _backendSongs = MutableStateFlow<List<BackendSong>>(emptyList())
    val backendSongs: StateFlow<List<BackendSong>> = _backendSongs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        loadLocalSongs()
        loadBackendSongs()
    }

    private fun loadLocalSongs() {
        coroutineScope.launch {
            songDao.getAllSongs().collect { songs ->
                _allSongs.value = songs
            }
        }
    }

    fun loadBackendSongs() {
        coroutineScope.launch {
            _isLoading.value = true
            try {
                val response = apiService.getAllSongs(limit = 100, offset = 0)
                if (response.isSuccessful && response.body()?.success == true) {
                    val songs = response.body()?.data?.songs ?: emptyList()
                    _backendSongs.value = songs
                    Log.d("MusicRepository", "Loaded ${songs.size} songs from backend")
                } else {
                    Log.e("MusicRepository", "Failed to load songs: ${response.body()?.error}")
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error loading backend songs", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getStreamUrl(songId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getStreamUrl(songId)
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.streamUrl
                } else {
                    Log.e("MusicRepository", "Failed to get stream URL: ${response.body()?.error}")
                    null
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error getting stream URL", e)
                null
            }
        }
    }

    suspend fun searchSongs(query: String): List<BackendSong> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.searchSongs(query)
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.songs ?: emptyList()
                } else {
                    Log.e("MusicRepository", "Search failed: ${response.body()?.error}")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error searching songs", e)
                emptyList()
            }
        }
    }

    suspend fun getSongsByArtist(artist: String): List<BackendSong> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getSongsByArtist(artist)
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.songs ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error getting songs by artist", e)
                emptyList()
            }
        }
    }

    suspend fun getPopularArtists(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getPopularArtists()
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.mapNotNull { it.artist } ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error getting popular artists", e)
                emptyList()
            }
        }
    }

    suspend fun getRecentSongs(): List<BackendSong> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getRecentSongs()
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.data?.songs ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("MusicRepository", "Error getting recent songs", e)
                emptyList()
            }
        }
    }

    fun backendSongToLocal(backendSong: BackendSong): Song {
        return Song(
            id = backendSong.id,
            title = backendSong.title,
            artist = backendSong.artist,
            album = backendSong.album,
            path = "", // Will be populated with stream URL
            duration = backendSong.duration ?: 0L,
            size = backendSong.fileSize ?: 0L
        )
    }

    fun scanDeviceForMusic() {
        coroutineScope.launch {
            val songs = mutableListOf<Song>()

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->

                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getString(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val path = cursor.getString(dataColumn)
                    val duration = cursor.getLong(durationColumn)
                    val size = cursor.getLong(sizeColumn)

                    if (File(path).exists()) {
                        songs.add(Song(id, title, artist, album, path, duration, size))
                    }
                }
            }

            songDao.insertAll(songs)
            Log.d("MusicRepository", "Scanned and found ${songs.size} music files")
        }
    }

    private fun calculateSHA256(data: ByteArray): String {
        return data.sha256()
    }

    private fun getFileExtension(path: String): String {
        return path.substringAfterLast('.', "")
    }
}