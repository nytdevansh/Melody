package com.example.melody.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.melody.MainActivity
import com.example.melody.R
import com.example.melody.MusicRepository
import com.example.melody.data.Song

class MusicPlayerService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null
    private var musicRepository: MusicRepository? = null
    private val binder = MusicPlayerBinder()

    companion object {
        const val ACTION_PLAY_SONG = "ACTION_PLAY_SONG"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SONG_ID = "EXTRA_SONG_ID"

        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "MusicPlayerChannel"
        private const val TAG = "MusicPlayerService"
    }

    inner class MusicPlayerBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        musicRepository = MusicRepository(this)
        createNotificationChannel()
        Log.d(TAG, "MusicPlayerService created")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_SONG -> {
                val songId = intent.getStringExtra(EXTRA_SONG_ID)
                if (songId != null) {
                    playSong(songId)
                }
            }
            ACTION_PAUSE -> pauseMusic()
            ACTION_RESUME -> resumeMusic()
            ACTION_STOP -> stopMusic()
        }
        return START_STICKY
    }

    private fun playSong(songId: String) {
        try {
            val song = musicRepository?.getSongById(songId)
            if (song != null) {
                currentSong = song

                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(song.filePath)
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        startForeground(NOTIFICATION_ID, createNotification())
                        Log.d(TAG, "Playing: ${song.title}")
                    }
                    setOnCompletionListener {
                        // Handle song completion
                        Log.d(TAG, "Song completed: ${song.title}")
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing song", e)
        }
    }

    private fun pauseMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                Log.d(TAG, "Music paused")
            }
        }
    }

    private fun resumeMusic() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                Log.d(TAG, "Music resumed")
            }
        }
    }

    private fun stopMusic() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        currentSong = null
        stopForeground(true)
        stopSelf()
        Log.d(TAG, "Music stopped")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music Player Service Channel"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Player")
            .setContentText(currentSong?.title ?: "Playing music")
            .setSmallIcon(R.drawable.ic_music_note) // You'll need to add this icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    fun getCurrentSong(): Song? = currentSong

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d(TAG, "MusicPlayerService destroyed")
    }
}