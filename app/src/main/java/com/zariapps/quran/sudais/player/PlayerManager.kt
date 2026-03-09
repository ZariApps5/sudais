package com.zariapps.quran.sudais.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.zariapps.quran.sudais.config.ReciterConfig
import com.zariapps.quran.sudais.data.local.DownloadDao
import com.zariapps.quran.sudais.data.repository.SurahRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerManager @Inject constructor(
    val exoPlayer: ExoPlayer,
    private val downloadDao: DownloadDao,
    private val surahRepository: SurahRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _currentSurahNumber = MutableStateFlow<Int?>(null)
    val currentSurahNumber: StateFlow<Int?> = _currentSurahNumber.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private var positionUpdateJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) startPositionUpdates() else stopPositionUpdates()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    _duration.value = exoPlayer.duration
                }
                if (state == Player.STATE_ENDED) {
                    playNext()
                }
            }
        })
    }

    suspend fun play(surahNumber: Int) {
        _currentSurahNumber.value = surahNumber
        val download = downloadDao.getDownload(surahNumber)
        val uri = if (download != null && File(download.filePath).exists()) {
            download.filePath
        } else {
            ReciterConfig.getAudioUrl(surahNumber)
        }

        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
        saveCurrentPosition()
    }

    fun resume() {
        exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun setSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    fun playNext() {
        val current = _currentSurahNumber.value ?: return
        if (current < 114) {
            scope.launch { play(current + 1) }
        }
    }

    fun playPrevious() {
        val current = _currentSurahNumber.value ?: return
        if (exoPlayer.currentPosition > 3000) {
            seekTo(0)
        } else if (current > 1) {
            scope.launch { play(current - 1) }
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) pause() else resume()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (true) {
                _currentPosition.value = exoPlayer.currentPosition
                _duration.value = exoPlayer.duration.coerceAtLeast(0)
                delay(500)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        saveCurrentPosition()
    }

    private fun saveCurrentPosition() {
        val surah = _currentSurahNumber.value ?: return
        scope.launch {
            surahRepository.savePlaybackState(surah, exoPlayer.currentPosition)
        }
    }
}
