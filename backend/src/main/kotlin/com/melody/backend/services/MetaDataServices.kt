package com.melody.backend.services

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.AudioHeader
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.melody.backend.models.UploadRequest

data class AudioMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long, // in milliseconds
    val bitrate: Int?,
    val format: String,
    val year: Int?,
    val genre: String?,
    val fileSize: Long,
    val hash: String
)

class MetadataService {

    suspend fun extractMetadata(audioData: ByteArray, originalFileName: String? = null): AudioMetadata = withContext(Dispatchers.IO) {
        // Create temporary file
        val tempFile = File.createTempFile("audio_", ".tmp")

        try {
            // Write data to temp file
            FileOutputStream(tempFile).use { fos ->
                fos.write(audioData)
            }

            // Read audio file
            val audioFile = AudioFileIO.read(tempFile)
            val header: AudioHeader = audioFile.audioHeader
            val tag: Tag? = audioFile.tag

            // Extract metadata
            val title = tag?.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }
                ?: originalFileName?.substringBeforeLast('.')
                ?: "Unknown Title"

            val artist = tag?.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }
                ?: "Unknown Artist"

            val album = tag?.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }
                ?: "Unknown Album"

            val year = tag?.getFirst(FieldKey.YEAR)?.toIntOrNull()

            val genre = tag?.getFirst(FieldKey.GENRE)?.takeIf { it.isNotBlank() }

            val duration = (header.trackLength * 1000).toLong() // Convert to milliseconds

            val bitrate = header.bitRateAsNumber?.toInt() // Fixed: use bitRateAsNumber instead of bitRate

            val format = header.format?.lowercase() ?: getFormatFromFileName(originalFileName)

            // Calculate SHA-256 hash
            val hash = calculateSHA256(audioData)

            AudioMetadata(
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                bitrate = bitrate,
                format = format,
                year = year,
                genre = genre,
                fileSize = audioData.size.toLong(),
                hash = hash
            )

        } catch (e: Exception) {
            // Fallback metadata if extraction fails
            AudioMetadata(
                title = originalFileName?.substringBeforeLast('.') ?: "Unknown Title",
                artist = "Unknown Artist",
                album = "Unknown Album",
                duration = 0L,
                bitrate = null,
                format = getFormatFromFileName(originalFileName),
                year = null,
                genre = null,
                fileSize = audioData.size.toLong(),
                hash = calculateSHA256(audioData)
            )
        } finally {
            // Clean up temp file
            tempFile.delete()
        }
    }

    private fun getFormatFromFileName(fileName: String?): String {
        return fileName?.substringAfterLast('.', "mp3") ?: "mp3"
    }

    private fun calculateSHA256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun createUploadRequest(metadata: AudioMetadata): UploadRequest {
        return UploadRequest(
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            hash = metadata.hash,
            duration = metadata.duration,
            fileSize = metadata.fileSize,
            format = metadata.format,
            bitrate = metadata.bitrate,
            year = metadata.year,
            genre = metadata.genre
        )
    }

    /**
     * Validates audio file format
     */
    fun isSupportedFormat(fileName: String?): Boolean {
        if (fileName == null) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("mp3", "flac", "aac", "ogg", "wav", "m4a")
    }

    /**
     * Gets MIME type for audio format
     */
    fun getMimeType(format: String): String {
        return when (format.lowercase()) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            else -> "audio/mpeg"
        }
    }

    /**
     * Estimates quality level based on bitrate
     */
    fun getQualityLevel(bitrate: Int?): String {
        return when {
            bitrate == null -> "unknown"
            bitrate >= 320 -> "high"
            bitrate >= 192 -> "medium"
            bitrate >= 128 -> "standard"
            else -> "low"
        }
    }

    /**
     * Formats duration for display
     */
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    /**
     * Formats file size for display
     */
    fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "${sizeBytes}B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024}KB"
            sizeBytes < 1024 * 1024 * 1024 -> String.format("%.1fMB", sizeBytes / (1024.0 * 1024.0))
            else -> String.format("%.1fGB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}