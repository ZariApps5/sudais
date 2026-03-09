package com.zariapps.quran.sudais.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE surahNumber = :surahNumber")
    suspend fun getDownload(surahNumber: Int): DownloadEntity?

    @Query("SELECT surahNumber FROM downloads")
    fun getDownloadedSurahNumbers(): Flow<List<Int>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE surahNumber = :surahNumber")
    suspend fun delete(surahNumber: Int)

    @Query("DELETE FROM downloads")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM downloads")
    suspend fun count(): Int

    @Query("SELECT surahNumber FROM downloads")
    suspend fun getDownloadedNumbersOnce(): List<Int>

    @Query("SELECT * FROM downloads")
    suspend fun getAllDownloadsOnce(): List<DownloadEntity>

    @Query("SELECT SUM(fileSize) FROM downloads")
    fun getTotalSize(): Flow<Long?>
}
