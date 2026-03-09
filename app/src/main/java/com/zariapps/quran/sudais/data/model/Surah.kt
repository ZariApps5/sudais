package com.zariapps.quran.sudais.data.model

data class Surah(
    val number: Int,
    val nameArabic: String,
    val nameEnglish: String,
    val nameTranslation: String,
    val ayahCount: Int,
    val revelationType: String,
    val isFavorite: Boolean = false,
    val isDownloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false
)

enum class SurahFilter {
    ALL, FAVORITES
}
