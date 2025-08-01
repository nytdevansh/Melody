package com.example.melody

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.melody.data.Song
import com.example.melody.data.MelodyDatabase
import com.example.melody.data.SongDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicRepository(private val context: Context) {

    private val database = MelodyDatabase.getDatabase(context)
    private val songDao: SongDao = database.songDao()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Expose songs as Flow for reactive updates
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()

    // Get songs synchronously for immediate use
    suspend fun getAllSongsSync(): List<Song> = songDao.getAllSongsSync()

    companion object {
        private const val TAG = "MusicRepository"
    }

    fun scanDeviceForMusic() {
        coroutineScope.launch {
            try {
                val scannedSongs = scanMediaStore()
                if (scannedSongs.isNotEmpty()) {
                    // Clear existing songs and insert new ones
                    songDao.deleteAllSongs()
                    songDao.insertSongs(scannedSongs)
                    Log.d(TAG, "Saved ${scannedSongs.size} songs to database")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning device for music: ${e.message}")
            }
        }
    }

    private suspend fun scanMediaStore(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()

        val contentResolver: ContentResolver = context.contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor: Cursor? = contentResolver.query(
            uri,
            projection,
            selection,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

            while (it.moveToNext()) {
                val id = it.getString(idColumn)
                val title = it.getString(titleColumn) ?: "Unknown Title"
                val artist = it.getString(artistColumn) ?: "Unknown Artist"
                val album = it.getString(albumColumn) ?: "Unknown Album"
                val path = it.getString(pathColumn) ?: ""
                val duration = it.getLong(durationColumn)
                val size = it.getLong(sizeColumn)

                // Only add songs with valid duration and size
                if (duration > 0 && size > 0) {
                    val song = Song(id, title, artist, album, path, duration, size)
                    songs.add(song)
                }
            }
        }

        Log.d(TAG, "Scanned ${songs.size} music files from MediaStore")
        songs
    }

    suspend fun getSongById(id: String): Song? = songDao.getSongById(id)

    suspend fun getSongsByArtist(artist: String): List<Song> = songDao.getSongsByArtist(artist)

    suspend fun getSongsByAlbum(album: String): List<Song> = songDao.getSongsByAlbum(album)

    suspend fun searchSongs(query: String): List<Song> = songDao.searchSongs(query)

    suspend fun getAllArtists(): List<String> = songDao.getAllArtists()

    suspend fun getAllAlbums(): List<String> = songDao.getAllAlbums()

    suspend fun getSongCount(): Int = songDao.getSongCount()
}
