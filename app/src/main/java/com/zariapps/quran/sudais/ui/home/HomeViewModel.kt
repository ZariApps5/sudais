package com.zariapps.quran.sudais.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zariapps.quran.sudais.data.model.Surah
import com.zariapps.quran.sudais.data.model.SurahFilter
import com.zariapps.quran.sudais.data.repository.SurahRepository
import com.zariapps.quran.sudais.download.DownloadManager
import com.zariapps.quran.sudais.player.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val surahRepository: SurahRepository,
    private val downloadManager: DownloadManager,
    val playerManager: PlayerManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filter = MutableStateFlow(SurahFilter.ALL)
    val filter: StateFlow<SurahFilter> = _filter.asStateFlow()

    val surahList: StateFlow<List<Surah>> = combine(
        surahRepository.getAllSurahs(),
        _searchQuery,
        _filter
    ) { surahs, query, filter ->
        surahs
            .filter { surah ->
                when (filter) {
                    SurahFilter.ALL -> true
                    SurahFilter.FAVORITES -> surah.isFavorite
                }
            }
            .filter { surah ->
                if (query.isBlank()) true
                else {
                    surah.nameEnglish.contains(query, ignoreCase = true) ||
                            surah.nameArabic.contains(query) ||
                            surah.nameTranslation.contains(query, ignoreCase = true) ||
                            surah.number.toString() == query
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            surahRepository.initializeSurahs()
            // Silently download all surahs in the background for offline use.
            // The player will stream from the network until each file is ready.
            downloadManager.downloadAll()
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: SurahFilter) {
        _filter.value = filter
    }

    fun toggleFavorite(surahNumber: Int, currentState: Boolean) {
        viewModelScope.launch {
            surahRepository.toggleFavorite(surahNumber, !currentState)
        }
    }

    fun playSurah(surahNumber: Int) {
        viewModelScope.launch {
            playerManager.play(surahNumber)
        }
    }
}
