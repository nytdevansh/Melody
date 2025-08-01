package com.example.melody.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.melody.MainActivity
import com.example.melody.MusicRepository
import com.example.melody.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

class MusicPlayerService : Service(), MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null
    private var musicRepository: MusicRepository? = null
    private val binder = MusicPlayerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "MusicPlayerService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_player_channel"

        const val ACTION_PLAY_SONG = "com.example.melody.ACTION_PLAY_SONG"
        const val ACTION_PAUSE = "com.example.melody.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.melody.ACTION_RESUME"
        const val ACTION_STOP = "com.example.melody.ACTION_STOP"
        const val ACTION_NEXT = "com.example.melody.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.melody.ACTION_PREVIOUS"

        const val EXTRA_SONG_ID = "song_id"
    }

    inner class MusicPlayerBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        musicRepository = MusicRepository(this)
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_SONG -> {
                val songId = intent.getStringExtra(EXTRA_SONG_ID)
                songId?.let { playSong(it) }
            }
            ACTION_PAUSE -> pauseMusic()
            ACTION_RESUME -> resumeMusic()
            ACTION_STOP -> stopMusic()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
        }
        return START_STICKY
    }

    private fun playSong(songId: String) {
        serviceScope.launch {
            val song = musicRepository?.getSongById(songId)
            if (song == null) {
                Log.e(TAG, "Song not found: $songId")
                return@launch
            }

            try {
                // Release previous MediaPlayer if exists
                mediaPlayer?.release()

                // Create new MediaPlayer
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@MusicPlayerService, Uri.parse("file://${song.path}"))
                    setOnCompletionListener(this@MusicPlayerService)
                    setOnErrorListener(this@MusicPlayerService)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        mp.start()
                        currentSong = song
                        startForeground(NOTIFICATION_ID, createNotification())
                        Log.d(TAG, "Started playing: ${song.title}")
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error playing song: ${e.message}")
            }
        }
    }

    private fun pauseMusic() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                updateNotification()
                Log.d(TAG, "Music paused")
            }
        }
    }

    private fun resumeMusic() {
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                updateNotification()
                Log.d(TAG, "Music resumed")
            }
        }
    }

    private fun stopMusic() {
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        currentSong = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "Music stopped")
    }

    private fun playNext() {
        // TODO: Implement playlist functionality
        Log.d(TAG, "Next song requested - not implemented yet")
    }

    private fun playPrevious() {
        // TODO: Implement playlist functionality
        Log.d(TAG, "Previous song requested - not implemented yet")
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    fun getCurrentSong(): Song? {
        return currentSong
    }

    fun getCurrentPosition(): Int {
        return mediaPlayer?.currentPosition ?: 0
    }

    fun getDuration(): Int {
        return mediaPlayer?.duration ?: 0
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    override fun onCompletion(mp: MediaPlayer?) {
        Log.d(TAG, "Song completed")
        // TODO: Auto-play next song in playlist
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
        return false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music player controls"
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

        val playPauseAction = if (isPlaying()) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                createActionPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                createActionPendingIntent(ACTION_RESUME)
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong?.title ?: "Unknown")
            .setContentText(currentSong?.artist ?: "Unknown Artist")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0)
            )
            .build()
    }

    private fun updateNotification() {
        if (currentSong != null) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMusic()
        Log.d(TAG, "Service destroyed")
    }
}