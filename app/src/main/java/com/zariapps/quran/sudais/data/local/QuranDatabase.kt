package com.zariapps.quran.sudais.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SurahEntity::class, DownloadEntity::class, PlaybackStateEntity::class],
    version = 1,
    exportSchema = false
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun surahDao(): SurahDao
    abstract fun downloadDao(): DownloadDao
    abstract fun playbackDao(): PlaybackDao
}
