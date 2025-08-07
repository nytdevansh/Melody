package com.melody.backend.models


import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val hash: String,
    val b2Url: String? = null,
    val duration: Long, // milliseconds
    val fileSize: Long, // bytes
    val format: String, // mp3, flac, aac
    val bitrate: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val uploadedAt: String? = null
)

@Serializable
data class UploadRequest(
    val title: String,
    val artist: String,
    val album: String,
    val hash: String,
    val duration: Long,
    val fileSize: Long,
    val format: String,
    val bitrate: Int? = null,
    val year: Int? = null,
    val genre: String? = null
)

@Serializable
data class UploadResponse(
    val success: Boolean,
    val uploadUrl: String? = null,
    val songId: String? = null,
    val message: String? = null,
    val song: Song? = null
)

@Serializable
data class ExistsResponse(
    val exists: Boolean,
    val song: Song? = null
)

@Serializable
data class StreamResponse(
    val streamUrl: String,
    val expiresAt: Long? = null
)

@Serializable
data class SearchResponse(
    val songs: List<Song>,
    val total: Int
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: String? = null
)