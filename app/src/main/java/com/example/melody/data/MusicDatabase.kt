package com.example.melody.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [Song::class],
    version = 1,
    exportSchema = false
)
abstract class MelodyDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: MelodyDatabase? = null

        fun getDatabase(context: Context): MelodyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MelodyDatabase::class.java,
                    "melody_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}