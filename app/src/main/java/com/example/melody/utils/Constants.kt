// Location: app/src/main/java/com/example/melody/utils/Constants.kt
package com.example.melody.utils

object Constants {
    // Network Configuration
    const val BASE_URL = "https://your-backend-url.com/" // TODO: Replace with your actual backend URL
    const val CONNECT_TIMEOUT = 30L // seconds
    const val READ_TIMEOUT = 60L // seconds
    const val WRITE_TIMEOUT = 60L // seconds

    // API Endpoints
    const val ENDPOINT_SONGS = "api/music/songs"
    const val ENDPOINT_UPLOAD = "api/music/upload"
    const val ENDPOINT_SEARCH = "api/music/search"
    const val ENDPOINT_STREAM = "api/music/stream"
    const val ENDPOINT_EXISTS = "api/music/exists"
    const val ENDPOINT_POPULAR_ARTISTS = "api/music/popular/artists"
    const val ENDPOINT_POPULAR_GENRES = "api/music/popular/genres"
    const val ENDPOINT_RECENT = "api/music/recent"
    const val ENDPOINT_HEALTH = "health"

    // Pagination
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 100

    // File Upload
    const val MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB
    const val SUPPORTED_AUDIO_FORMATS = "mp3,flac,aac,ogg,wav,m4a"

    // Cache
    const val CACHE_EXPIRY_HOURS = 24
    const val MAX_CACHE_SIZE = 100 * 1024 * 1024 // 100MB

    // Preferences
    const val PREF_NAME = "melody_preferences"
    const val PREF_BACKEND_URL = "backend_url"
    const val PREF_AUTO_UPLOAD = "auto_upload"
    const val PREF_WIFI_ONLY = "wifi_only"
    const val PREF_AUDIO_QUALITY = "audio_quality"

    // Work Manager
    const val WORK_UPLOAD_SONGS = "upload_songs"
    const val WORK_SYNC_LIBRARY = "sync_library"

    // Error Messages
    const val ERROR_NETWORK = "Network error occurred"
    const val ERROR_FILE_NOT_FOUND = "Audio file not found"
    const val ERROR_UPLOAD_FAILED = "Failed to upload song"
    const val ERROR_STREAM_FAILED = "Failed to get stream URL"
    const val ERROR_SEARCH_FAILED = "Search failed"

    // Audio Quality
    enum class AudioQuality(val bitrate: Int, val displayName: String) {
        LOW(128, "Low (128kbps)"),
        MEDIUM(192, "Medium (192kbps)"),
        HIGH(256, "High (256kbps)"),
        VERY_HIGH(320, "Very High (320kbps)"),
        LOSSLESS(1411, "Lossless (FLAC)")
    }

    // Sync Status
    enum class SyncStatus {
        NOT_SYNCED,
        SYNCING,
        SYNCED,
        FAILED
    }
}