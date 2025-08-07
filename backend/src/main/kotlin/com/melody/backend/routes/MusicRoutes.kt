package com.melody.backend.routes

import com.melody.backend.models.*
import com.melody.backend.services.B2Service
import com.melody.backend.services.MusicService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

fun Route.musicRoutes() {
    val musicService = MusicService()
    val b2Service = B2Service(
        keyId = System.getenv("B2_KEY_ID") ?: "your_b2_key_id",
        applicationKey = System.getenv("B2_APPLICATION_KEY") ?: "your_b2_application_key",
        bucketId = System.getenv("B2_BUCKET_ID") ?: "your_bucket_id",
        bucketName = System.getenv("B2_BUCKET_NAME") ?: "melody-music-storage",
        cdnDomain = System.getenv("CDN_DOMAIN") // Optional: your Cloudflare CDN domain
    )

    route("/api/music") {

        // Check if song exists by hash
        get("/exists/{hash}") {
            val hash = call.parameters["hash"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<ExistsResponse>(
                    success = false,
                    error = "Missing hash parameter"
                )
            )

            try {
                val song = musicService.getSongByHash(hash)
                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = ExistsResponse(
                            exists = song != null,
                            song = song
                        )
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<ExistsResponse>(
                        success = false,
                        error = "Error checking song existence: ${e.message}"
                    )
                )
            }
        }

        // Upload song with multipart form data
        post("/upload") {
            try {
                val multipart = call.receiveMultipart()
                var uploadRequest: UploadRequest? = null
                var audioData: ByteArray? = null
                var fileName: String? = null

                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            if (part.name == "metadata") {
                                uploadRequest = Json.decodeFromString<UploadRequest>(part.value)
                            }
                        }
                        is PartData.FileItem -> {
                            if (part.name == "audio") {
                                fileName = part.originalFileName
                                audioData = part.streamProvider().readBytes()
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }

                if (uploadRequest == null || audioData == null) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ApiResponse<UploadResponse>(
                            success = false,
                            error = "Missing metadata or audio file"
                        )
                    )
                }

                val request = uploadRequest!!
                val data = audioData!!

                // Check if song already exists
                val existingSong = musicService.getSongByHash(request.hash)
                if (existingSong != null) {
                    return@post call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            data = UploadResponse(
                                success = true,
                                songId = existingSong.id,
                                message = "Song already exists",
                                song = existingSong
                            )
                        )
                    )
                }

                // Generate filename for B2
                val cleanTitle = request.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                val cleanArtist = request.artist.replace(Regex("[^a-zA-Z0-9]"), "_")
                val b2FileName = "tracks/${cleanArtist}/${cleanTitle}_${request.hash.take(8)}.${request.format}"

                // Upload to B2
                val b2Url = b2Service.uploadFile(
                    fileName = b2FileName,
                    fileData = data,
                    contentType = getContentType(request.format)
                )

                if (b2Url == null) {
                    return@post call.respond(
                        HttpStatusCode.InternalServerError,
                        ApiResponse<UploadResponse>(
                            success = false,
                            error = "Failed to upload to B2"
                        )
                    )
                }

                // Save to database
                val song = musicService.insertSong(request, b2Url)

                call.respond(
                    HttpStatusCode.Created,
                    ApiResponse(
                        success = true,
                        data = UploadResponse(
                            success = true,
                            songId = song.id,
                            message = "Song uploaded successfully",
                            song = song
                        )
                    )
                )

            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<UploadResponse>(
                        success = false,
                        error = "Upload failed: ${e.message}"
                    )
                )
            }
        }

        // Get all songs with pagination
        get("/songs") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0

                val songs = musicService.getAllSongs(limit, offset)
                val total = musicService.getTotalSongCount()

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = SearchResponse(songs = songs, total = total.toInt())
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<SearchResponse>(
                        success = false,
                        error = "Error fetching songs: ${e.message}"
                    )
                )
            }
        }

        // Get song by ID
        get("/songs/{id}") {
            val songId = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<Song>(
                    success = false,
                    error = "Missing song ID"
                )
            )

            try {
                val song = musicService.getSongById(songId)
                if (song != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(success = true, data = song)
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<Song>(
                            success = false,
                            error = "Song not found"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<Song>(
                        success = false,
                        error = "Error fetching song: ${e.message}"
                    )
                )
            }
        }

        // Get stream URL for a song
        get("/stream/{id}") {
            val songId = call.parameters["id"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<StreamResponse>(
                    success = false,
                    error = "Missing song ID"
                )
            )

            try {
                val song = musicService.getSongById(songId)
                if (song?.b2Url != null) {
                    call.respond(
                        HttpStatusCode.OK,
                        ApiResponse(
                            success = true,
                            data = StreamResponse(
                                streamUrl = song.b2Url,
                                expiresAt = null // B2 public URLs don't expire
                            )
                        )
                    )
                } else {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse<StreamResponse>(
                            success = false,
                            error = "Song not found or not available for streaming"
                        )
                    )
                }
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<StreamResponse>(
                        success = false,
                        error = "Error getting stream URL: ${e.message}"
                    )
                )
            }
        }

        // Search songs
        get("/search") {
            val query = call.request.queryParameters["q"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<SearchResponse>(
                    success = false,
                    error = "Missing search query"
                )
            )

            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val songs = musicService.searchSongs(query, limit)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = SearchResponse(songs = songs, total = songs.size)
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<SearchResponse>(
                        success = false,
                        error = "Search failed: ${e.message}"
                    )
                )
            }
        }

        // Get songs by artist
        get("/artists/{artist}/songs") {
            val artist = call.parameters["artist"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<SearchResponse>(
                    success = false,
                    error = "Missing artist name"
                )
            )

            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val songs = musicService.getSongsByArtist(artist, limit)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = SearchResponse(songs = songs, total = songs.size)
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<SearchResponse>(
                        success = false,
                        error = "Error fetching artist songs: ${e.message}"
                    )
                )
            }
        }

        // Get songs by album
        get("/albums/{album}/songs") {
            val album = call.parameters["album"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ApiResponse<SearchResponse>(
                    success = false,
                    error = "Missing album name"
                )
            )

            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val songs = musicService.getSongsByAlbum(album, limit)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = SearchResponse(songs = songs, total = songs.size)
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<SearchResponse>(
                        success = false,
                        error = "Error fetching album songs: ${e.message}"
                    )
                )
            }
        }

        // Get popular artists
        get("/popular/artists") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val artists = musicService.getPopularArtists(limit)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = artists.map { (artist, count) ->
                            mapOf("artist" to artist, "songCount" to count)
                        }
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<Map<String, Any>>>(
                        success = false,
                        error = "Error fetching popular artists: ${e.message}"
                    )
                )
            }
        }

        // Get popular genres
        get("/popular/genres") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val genres = musicService.getPopularGenres(limit)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = genres.map { (genre, count) ->
                            mapOf("genre" to genre, "songCount" to count)
                        }
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<List<Map<String, Any>>>(
                        success = false,
                        error = "Error fetching popular genres: ${e.message}"
                    )
                )
            }
        }

        // Get recent songs
        get("/recent") {
            try {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 20
                val songs = musicService.getRecentSongs(limit)

                call.respond(
                    HttpStatusCode.OK,
                    ApiResponse(
                        success = true,
                        data = SearchResponse(songs = songs, total = songs.size)
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse<SearchResponse>(
                        success = false,
                        error = "Error fetching recent songs: ${e.message}"
                    )
                )
            }
        }
    }
}

private fun getContentType(format: String): String {
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