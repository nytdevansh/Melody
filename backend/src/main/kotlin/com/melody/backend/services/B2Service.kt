package com.melody.backend.services


import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*
import java.io.IOException

class B2Service(
    private val keyId: String,
    private val applicationKey: String,
    private val bucketId: String,
    private val bucketName: String,
    private val cdnDomain: String? = null
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class AuthResponse(
        val authorizationToken: String,
        val apiUrl: String,
        val downloadUrl: String
    )

    @Serializable
    private data class UploadUrlResponse(
        val uploadUrl: String,
        val authorizationToken: String
    )

    @Serializable
    private data class FileUploadResponse(
        val fileId: String,
        val fileName: String,
        val contentLength: Long,
        val contentSha1: String? = null,
        val fileInfo: Map<String, String>? = null
    )

    private var authToken: String? = null
    private var apiUrl: String? = null
    private var downloadUrl: String? = null

    suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        val credentials = Base64.getEncoder().encodeToString("$keyId:$applicationKey".toByteArray())

        val request = Request.Builder()
            .url("https://api.backblazeb2.com/b2api/v2/b2_authorize_account")
            .header("Authorization", "Basic $credentials")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val authResponse = json.decodeFromString<AuthResponse>(responseBody)
                    authToken = authResponse.authorizationToken
                    apiUrl = authResponse.apiUrl
                    downloadUrl = authResponse.downloadUrl
                    return@withContext true
                }
            }
            false
        } catch (e: IOException) {
            println("B2 Authentication failed: ${e.message}")
            false
        }
    }

    suspend fun getUploadUrl(): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (authToken == null && !authenticate()) {
            return@withContext null
        }

        val requestBody = """{"bucketId": "$bucketId"}""".toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$apiUrl/b2api/v2/b2_get_upload_url")
            .header("Authorization", authToken!!)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val uploadResponse = json.decodeFromString<UploadUrlResponse>(responseBody)
                    return@withContext Pair(uploadResponse.uploadUrl, uploadResponse.authorizationToken)
                }
            }
            null
        } catch (e: IOException) {
            println("Failed to get upload URL: ${e.message}")
            null
        }
    }

    suspend fun uploadFile(
        fileName: String,
        fileData: ByteArray,
        contentType: String = "audio/mpeg",
        sha1Hash: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val uploadInfo = getUploadUrl() ?: return@withContext null
        val (uploadUrl, uploadToken) = uploadInfo

        val requestBody = fileData.toRequestBody(contentType.toMediaType())

        val request = Request.Builder()
            .url(uploadUrl)
            .header("Authorization", uploadToken)
            .header("X-Bz-File-Name", fileName)
            .header("Content-Type", contentType)
            .apply {
                if (sha1Hash != null) {
                    header("X-Bz-Content-Sha1", sha1Hash)
                }
            }
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    val uploadResponse = json.decodeFromString<FileUploadResponse>(responseBody)
                    return@withContext getPublicUrl(fileName)
                }
            }
            null
        } catch (e: IOException) {
            println("File upload failed: ${e.message}")
            null
        }
    }

    fun getPublicUrl(fileName: String): String {
        return if (cdnDomain != null) {
            "https://$cdnDomain/file/$bucketName/$fileName"
        } else {
            "$downloadUrl/file/$bucketName/$fileName"
        }
    }

    suspend fun deleteFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (authToken == null && !authenticate()) {
            return@withContext false
        }

        // First get file info
        val fileId = getFileId(fileName) ?: return@withContext false

        val requestBody = """{"fileId": "$fileId", "fileName": "$fileName"}""".toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$apiUrl/b2api/v2/b2_delete_file_version")
            .header("Authorization", authToken!!)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            println("File deletion failed: ${e.message}")
            false
        }
    }

    private suspend fun getFileId(fileName: String): String? = withContext(Dispatchers.IO) {
        if (authToken == null && !authenticate()) {
            return@withContext null
        }

        val requestBody = """{"bucketId": "$bucketId", "startFileName": "$fileName", "maxFileCount": 1}""".toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$apiUrl/b2api/v2/b2_list_file_names")
            .header("Authorization", authToken!!)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                // Parse response to get file ID
                // This is simplified - you'd need to parse the JSON response properly
                return@withContext null // Placeholder
            }
            null
        } catch (e: IOException) {
            println("Failed to get file ID: ${e.message}")
            null
        }
    }
}