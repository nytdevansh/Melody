package com.example.melody

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import com.example.melody.data.Song

class MusicRepository(private val context: Context) {

    companion object {
        private const val TAG = "MusicRepository"
    }

    private val _allSongs = mutableListOf<Song>()
    val allSongs: List<Song> get() = _allSongs.toList()

    fun scanDeviceForMusic() {
        try {
            _allSongs.clear()

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 AND ${MediaStore.Audio.Media.SIZE} > 0"
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use { c ->
                val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val pathColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (c.moveToNext()) {
                    try {
                        val id = c.getLong(idColumn)
                        val title = c.getString(titleColumn) ?: "Unknown Title"
                        val artist = c.getString(artistColumn) ?: "Unknown Artist"
                        val album = c.getString(albumColumn) ?: "Unknown Album"
                        val path = c.getString(pathColumn) ?: continue
                        val duration = c.getLong(durationColumn)
                        val size = c.getLong(sizeColumn)

                        // Skip very small files (likely not real music)
                        if (size < 1024 * 100) continue // Skip files smaller than 100KB

                        val song = Song(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            filePath = path,
                            duration = duration,
                            size = size
                        )

                        _allSongs.add(song)

                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing song entry", e)
                        continue
                    }
                }
            }

            Log.d(TAG, "Scan completed. Found ${_allSongs.size} music files")

        } catch (e: Exception) {
            Log.e(TAG, "Error scanning device for music", e)
            throw e
        }
    }

    fun getSongById(id: String): Song? {
        return _allSongs.find { it.id == id }
    }

    fun getSongsByArtist(artist: String): List<Song> {
        return _allSongs.filter { it.artist.equals(artist, ignoreCase = true) }
    }

    fun getSongsByAlbum(album: String): List<Song> {
        return _allSongs.filter { it.album.equals(album, ignoreCase = true) }
    }

    fun searchSongs(query: String): List<Song> {
        val lowercaseQuery = query.lowercase()
        return _allSongs.filter { song ->
            song.title.lowercase().contains(lowercaseQuery) ||
                    song.artist.lowercase().contains(lowercaseQuery) ||
                    song.album.lowercase().contains(lowercaseQuery)
        }
    }
}