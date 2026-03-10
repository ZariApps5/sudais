package com.zariapps.quran.sudais.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SurahEntity::class, PlaybackStateEntity::class],
    version = 2,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun surahDao(): SurahDao
    abstract fun playbackDao(): PlaybackDao
}
