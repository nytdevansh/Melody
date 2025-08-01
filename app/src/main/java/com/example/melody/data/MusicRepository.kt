package com.example.melody

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.melody.data.Song

class MusicRepository(private val context: Context) {

    private val _allSongs = mutableListOf<Song>()
    val allSongs: List<Song> get() = _allSongs.toList()

    companion object {
        private const val TAG = "MusicRepository"
    }

    fun scanDeviceForMusic() {
        _allSongs.clear()

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
                    _allSongs.add(song)
                }
            }
        }

        Log.d(TAG, "Scanned ${_allSongs.size} music files")
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
        return _allSongs.filter { song ->
            song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true) ||
                    song.album.contains(query, ignoreCase = true)
        }
    }
}