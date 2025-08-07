package com.example.melody.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.melody.data.Song

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: String): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<Song>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song)

    @Delete
    suspend fun delete(song: Song)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()
}
