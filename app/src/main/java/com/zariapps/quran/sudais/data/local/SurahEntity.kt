package com.zariapps.quran.sudais.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "surahs")
data class SurahEntity(
    @PrimaryKey val number: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val nameTranslation: String,
    val ayahCount: Int,
    val revelationType: String,
    val isFavorite: Boolean = false
)
