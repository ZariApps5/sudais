package com.zariapps.quran.sudais.data.repository

import com.zariapps.quran.sudais.data.local.PlaybackDao
import com.zariapps.quran.sudais.data.local.PlaybackStateEntity
import com.zariapps.quran.sudais.data.local.SurahDao
import com.zariapps.quran.sudais.data.local.SurahEntity
import com.zariapps.quran.sudais.data.model.Surah
import com.zariapps.quran.sudais.data.model.SurahData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SurahRepository @Inject constructor(
    private val surahDao: SurahDao,
    private val playbackDao: PlaybackDao
) {
    suspend fun initializeSurahs() {
        if (surahDao.count() == 0) {
            surahDao.insertAll(SurahData.allSurahs)
        }
    }

    fun getAllSurahs(): Flow<List<Surah>> {
        return surahDao.getAllSurahs().map { surahs ->
            surahs.map { entity ->
                Surah(
                    number = entity.number,
                    nameArabic = entity.nameArabic,
                    nameEnglish = entity.nameEnglish,
                    nameTranslation = entity.nameTranslation,
                    ayahCount = entity.ayahCount,
                    revelationType = entity.revelationType,
                    isFavorite = entity.isFavorite,
                    isDownloaded = true  // all surahs are always available (bundled in assets)
                )
            }
        }
    }

    fun searchSurahs(query: String): Flow<List<SurahEntity>> = surahDao.searchSurahs(query)

    fun getFavorites(): Flow<List<SurahEntity>> = surahDao.getFavorites()

    suspend fun toggleFavorite(surahNumber: Int, isFavorite: Boolean) {
        surahDao.setFavorite(surahNumber, isFavorite)
    }

    fun getPlaybackState(): Flow<PlaybackStateEntity?> = playbackDao.getPlaybackState()

    suspend fun getPlaybackStateOnce(): PlaybackStateEntity? = playbackDao.getPlaybackStateOnce()

    suspend fun savePlaybackState(surahNumber: Int, position: Long) {
        playbackDao.savePlaybackState(
            PlaybackStateEntity(
                surahNumber = surahNumber,
                position = position,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
