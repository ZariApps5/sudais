package com.zariapps.quran.sudais.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val surahNumber: Int,
    val filePath: String,
    val fileSize: Long,
    val downloadedAt: Long
)
