package com.example.melody.data

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "songs")
@Parcelize
data class Song(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val duration: Long, // in milliseconds
    val size: Long // in bytes
) : Parcelable {

    /**
     * Returns formatted duration in MM:SS format
     */
    fun getFormattedDuration(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Returns formatted file size
     */
    fun getFormattedSize(): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> "${size / 1024}KB"
            else -> String.format("%.1fMB", size / (1024.0 * 1024.0))
        }
    }

    /**
     * Returns the file extension from the path
     */
    fun getFileExtension(): String {
        return path.substringAfterLast('.', "")
    }
}