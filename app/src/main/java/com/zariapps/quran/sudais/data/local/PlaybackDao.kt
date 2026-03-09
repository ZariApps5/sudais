package com.zariapps.quran.sudais.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback_state WHERE id = 1")
    fun getPlaybackState(): Flow<PlaybackStateEntity?>

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun getPlaybackStateOnce(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaybackState(state: PlaybackStateEntity)
}
