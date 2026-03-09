package com.zariapps.quran.sudais.ui.initialdownload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zariapps.quran.sudais.download.BulkDownloadState
import com.zariapps.quran.sudais.download.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InitialDownloadViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    val bulkDownloadState: StateFlow<BulkDownloadState> = downloadManager.bulkDownloadState

    // True once we confirm all files are already present (skip the screen entirely)
    private val _alreadyComplete = MutableStateFlow(false)
    val alreadyComplete: StateFlow<Boolean> = _alreadyComplete.asStateFlow()

    init {
        viewModelScope.launch {
            if (downloadManager.areAllDownloaded()) {
                _alreadyComplete.value = true
            } else {
                downloadManager.downloadAllSequentially()
            }
        }
    }
}
