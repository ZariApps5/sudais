package com.zariapps.quran.sudais.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zariapps.quran.sudais.data.model.SurahData
import com.zariapps.quran.sudais.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val playerManager: PlayerManager
) : ViewModel() {

    val surahs = SurahData.allSurahs

    fun playSurah(surahNumber: Int) {
        viewModelScope.launch {
            playerManager.play(surahNumber)
        }
    }
}
