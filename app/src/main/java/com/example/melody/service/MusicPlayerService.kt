// Location: app/src/main/java/com/example/melody/service/MusicPlayerService.kt
package com.example.melody.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.example.melody.R
import com.example.melody.data.Song
import com.example.melody.data.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicPlayerService : Service() {

    companion object {
        const val ACTION_PLAY_SONG = "action_play_song"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_RESUME = "action_resume"
        const val ACTION_STOP = "action_stop"
        const val EXTRA_SONG_ID = "extra_song_id"

        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "music_player_channel"
        private const val TAG = "MusicPlayerService"
    }

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var musicRepository: MusicRepository
    private var currentSong: Song? = null
    private val binder = MusicPlayerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    inner class MusicPlayerBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        musicRepository = MusicRepository(this)
        initializePlayer()
        createNotificationChannel()
    }

    private fun initializePlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Melody Music Player")

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        Log.d(TAG, "Player is ready")
                    }
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "Player is buffering")
                    }
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "Playback ended")
                    }
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "Player is idle")
                    }
                }
                updateNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "Is playing changed: $isPlaying")
                updateNotification()
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_PLAY_SONG -> {
                val songId = intent.getStringExtra(EXTRA_SONG_ID)
                if (songId != null) {
                    playSong(songId)
                }
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_RESUME -> resumePlayback()
            ACTION_STOP -> stopPlayback()
        }

        return START_NOT_STICKY
    }

    private fun playSong(songId: String) {
        Log.d(TAG, "Playing song with ID: $songId")

        serviceScope.launch {
            try {
                // Try to find song in local database first
                currentSong = musicRepository.allSongs.value.find { it.id == songId }

                if (currentSong != null) {
                    // Play local song
                    playLocalSong(currentSong!!)
                } else {
                    // Try to find in backend songs and get stream URL
                    val backendSong = musicRepository.backendSongs.value.find { it.id == songId }
                    if (backendSong != null) {
                        val streamUrl = musicRepository.getStreamUrl(songId)
                        if (streamUrl != null) {
                            currentSong = musicRepository.backendSongToLocal(backendSong).copy(path = streamUrl)
                            playStreamSong(currentSong!!, streamUrl)
                        } else {
                            Log.e(TAG, "Failed to get stream URL for song: $songId")
                        }
                    } else {
                        Log.e(TAG, "Song not found: $songId")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing song", e)
            }
        }
    }

    private fun playLocalSong(song: Song) {
        val mediaItem = MediaItem.Builder()
            .setUri(song.path)
            .setMediaId(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build()
            )
            .build()

        playMediaItem(mediaItem)
    }

    private fun playStreamSong(song: Song, streamUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaId(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .build()
            )
            .build()

        playMediaItem(mediaItem)
    }

    private fun playMediaItem(mediaItem: MediaItem) {
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "Started playing: ${currentSong?.title}")
    }

    private fun pausePlayback() {
        exoPlayer.playWhenReady = false
        updateNotification()
        Log.d(TAG, "Paused playback")
    }

    private fun resumePlayback() {
        exoPlayer.playWhenReady = true
        updateNotification()
        Log.d(TAG, "Resumed playback")
    }

    private fun stopPlayback() {
        exoPlayer.stop()
        currentSong = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Stopped playback")
    }

    fun isPlaying(): Boolean = exoPlayer.isPlaying

    fun getCurrentSong(): Song? = currentSong

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for music playback"
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val song = currentSong
        val title = song?.title ?: "Unknown"
        val artist = song?.artist ?: "Unknown Artist"

        // Create a simple colored bitmap instead of using drawable resource
        val iconBitmap = createSimpleIconBitmap()

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.ic_media_play) // Use system icon
            .setLargeIcon(iconBitmap)
            .addAction(createNotificationAction())
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        return notification
    }

    private fun createSimpleIconBitmap(): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = android.graphics.Color.BLUE
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return bitmap
    }

    private fun createNotificationAction(): NotificationCompat.Action {
        val isPlaying = exoPlayer.isPlaying
        val actionText = if (isPlaying) "Pause" else "Play"
        val actionIntent = if (isPlaying) ACTION_PAUSE else ACTION_RESUME

        val intent = Intent(this, MusicPlayerService::class.java).apply {
            action = actionIntent
        }

        val pendingIntent = PendingIntent.getService(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_pause, // Use system icon
            actionText,
            pendingIntent
        ).build()
    }

    private fun updateNotification() {
        if (currentSong != null) {
            val notification = createNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
        Log.d(TAG, "Service destroyed")
    }
}
