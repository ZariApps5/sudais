package com.zariapps.quran.sudais.ui.player

import androidx.lifecycle.ViewModel
import com.zariapps.quran.sudais.data.model.SurahData
import com.zariapps.quran.sudais.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerManager: PlayerManager
) : ViewModel() {

    val currentSurahNumber: StateFlow<Int?> = playerManager.currentSurahNumber
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val playbackSpeed: StateFlow<Float> = playerManager.playbackSpeed

    fun getSurahInfo(number: Int) = SurahData.allSurahs.find { it.number == number }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun seekTo(position: Long) = playerManager.seekTo(position)
    fun playNext() = playerManager.playNext()
    fun playPrevious() = playerManager.playPrevious()
    fun setSpeed(speed: Float) = playerManager.setSpeed(speed)
}
