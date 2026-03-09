package com.zariapps.quran.sudais.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 1,
    val surahNumber: Int,
    val position: Long,
    val updatedAt: Long
)
