package com.zariapps.quran.sudais.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zariapps.quran.sudais.data.local.DownloadEntity
import com.zariapps.quran.sudais.data.model.SurahData
import com.zariapps.quran.sudais.download.DownloadManager
import com.zariapps.quran.sudais.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager,
    private val playerManager: PlayerManager
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadManager.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalSize: StateFlow<Long?> = downloadManager.getTotalSize()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun getSurahName(number: Int): String {
        return SurahData.allSurahs.find { it.number == number }?.nameEnglish ?: "Surah $number"
    }

    fun getSurahNameArabic(number: Int): String {
        return SurahData.allSurahs.find { it.number == number }?.nameArabic ?: ""
    }

    fun deleteDownload(surahNumber: Int) {
        viewModelScope.launch {
            downloadManager.deleteDownload(surahNumber)
        }
    }

    fun playSurah(surahNumber: Int) {
        viewModelScope.launch {
            playerManager.play(surahNumber)
        }
    }
}
