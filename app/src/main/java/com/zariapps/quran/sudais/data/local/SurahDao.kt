package com.zariapps.quran.sudais.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SurahDao {
    @Query("SELECT * FROM surahs ORDER BY number ASC")
    fun getAllSurahs(): Flow<List<SurahEntity>>

    @Query("SELECT * FROM surahs WHERE number = :number")
    suspend fun getSurah(number: Int): SurahEntity?

    @Query("SELECT * FROM surahs WHERE isFavorite = 1 ORDER BY number ASC")
    fun getFavorites(): Flow<List<SurahEntity>>

    @Query("SELECT * FROM surahs WHERE nameEnglish LIKE '%' || :query || '%' OR nameArabic LIKE '%' || :query || '%' OR nameTranslation LIKE '%' || :query || '%' ORDER BY number ASC")
    fun searchSurahs(query: String): Flow<List<SurahEntity>>

    @Query("UPDATE surahs SET isFavorite = :isFavorite WHERE number = :surahNumber")
    suspend fun setFavorite(surahNumber: Int, isFavorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(surahs: List<SurahEntity>)

    @Query("SELECT COUNT(*) FROM surahs")
    suspend fun count(): Int
}
