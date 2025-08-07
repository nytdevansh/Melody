// Location: app/src/main/java/com/example/melody/worker/UploadWorker.kt
package com.example.melody.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.melody.data.MusicRepository
import com.example.melody.data.database.MusicDatabase  // Fixed import path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val SONG_ID_KEY = "song_id"
        const val PROGRESS_KEY = "progress"
        const val TAG = "UploadWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(SONG_ID_KEY) ?: return@withContext Result.failure()

        try {
            Log.d(TAG, "Starting upload for song ID: $songId")

            val musicRepository = MusicRepository(applicationContext)
            val database = MusicDatabase.getDatabase(applicationContext)
            val songDao = database.songDao()

            // Get the song from local database
            val song = songDao.getSongById(songId)
            if (song == null) {
                Log.e(TAG, "Song not found in database: $songId")
                return@withContext Result.failure()
            }

            // Set progress to starting
            setProgress(workDataOf(PROGRESS_KEY to 0))

            // Upload the song - need to create this method in MusicRepository
            // For now, just return success
            Log.d(TAG, "Upload functionality not yet implemented for song: ${song.title}")
            setProgress(workDataOf(PROGRESS_KEY to 100))
            return@withContext Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Error during upload", e)
            return@withContext Result.failure(
                workDataOf("error" to (e.message ?: "Unknown error"))
            )
        }
    }
}
