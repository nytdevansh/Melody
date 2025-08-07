// Location: app/src/main/java/com/example/melody/network/ApiService.kt
package com.example.melody.network

import com.example.melody.data.api.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @GET("api/music/songs")
    suspend fun getAllSongs(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Long = 0
    ): Response<ApiResponse<SearchResponse>>

    @GET("api/music/songs/{id}")
    suspend fun getSongById(
        @Path("id") songId: String
    ): Response<ApiResponse<BackendSong>>

    @GET("api/music/stream/{id}")
    suspend fun getStreamUrl(
        @Path("id") songId: String
    ): Response<ApiResponse<StreamResponse>>

    @GET("api/music/exists/{hash}")
    suspend fun checkSongExists(
        @Path("hash") hash: String
    ): Response<ApiResponse<ExistsResponse>>

    @Multipart
    @POST("api/music/upload")
    suspend fun uploadSong(
        @Part("metadata") metadata: RequestBody,
        @Part audio: MultipartBody.Part
    ): Response<ApiResponse<UploadResponse>>

    @GET("api/music/search")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<SearchResponse>>

    @GET("api/music/artists/{artist}/songs")
    suspend fun getSongsByArtist(
        @Path("artist") artist: String,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<SearchResponse>>

    @GET("api/music/albums/{album}/songs")
    suspend fun getSongsByAlbum(
        @Path("album") album: String,
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<SearchResponse>>

    @GET("api/music/popular/artists")
    suspend fun getPopularArtists(
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<PopularItem>>>

    @GET("api/music/popular/genres")
    suspend fun getPopularGenres(
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<List<PopularItem>>>

    @GET("api/music/recent")
    suspend fun getRecentSongs(
        @Query("limit") limit: Int = 20
    ): Response<ApiResponse<SearchResponse>>

    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>
}