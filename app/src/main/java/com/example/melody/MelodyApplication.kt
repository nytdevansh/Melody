// Location: app/src/main/java/com/example/melody/MelodyApplication.kt
package com.example.melody

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager

class MelodyApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "MelodyApplication"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application created")
    }
}
