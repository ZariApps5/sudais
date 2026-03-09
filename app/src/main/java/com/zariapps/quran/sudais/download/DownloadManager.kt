package com.zariapps.quran.sudais.download

import android.content.Context
import android.util.Log
import com.zariapps.quran.sudais.audio.AudioTranscoder
import com.zariapps.quran.sudais.config.ReciterConfig
import com.zariapps.quran.sudais.data.local.DownloadDao
import com.zariapps.quran.sudais.data.local.DownloadEntity
import com.zariapps.quran.sudais.data.model.SurahData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DownloadManager"

// Stall threshold: if no new file completes within this time, watchdog triggers a restart
private const val STALL_THRESHOLD_MS = 5 * 60 * 1000L   // 5 minutes
private const val WATCHDOG_INTERVAL_MS = 60 * 1000L      // check every 60 seconds

data class DownloadProgress(
    val surahNumber: Int,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val error: String? = null
)

data class BulkDownloadState(
    val totalFiles: Int = 114,
    val completedFiles: Int = 0,
    val isRunning: Boolean = false,
    val isDone: Boolean = false
)

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val downloadDao: DownloadDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow<Map<Int, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val _bulkDownloadState = MutableStateFlow(BulkDownloadState())
    val bulkDownloadState: StateFlow<BulkDownloadState> = _bulkDownloadState.asStateFlow()

    // Max 2 simultaneous transcodings — avoids thermal throttling alongside downloads
    private val transcodeSemaphore = Semaphore(2)

    // Reference to the active bulk download job so the watchdog can cancel it
    private var bulkJob: Job? = null

    suspend fun areAllDownloaded(): Boolean = downloadDao.count() >= 114

    /**
     * Downloads all 114 surahs 5 at a time, shortest-first.
     * A watchdog monitors progress and auto-restarts if downloads stall.
     */
    fun downloadAll() {
        if (_bulkDownloadState.value.isRunning) return
        startBulkDownload()
        startWatchdog()
    }

    private fun startBulkDownload() {
        bulkJob = scope.launch {
            val alreadyDownloaded = downloadDao.getDownloadedNumbersOnce().toSet()

            val toDownload = SurahData.allSurahs
                .filter { it.number !in alreadyDownloaded }
                .sortedBy { it.ayahCount }
                .map { it.number }

            if (toDownload.isEmpty()) {
                _bulkDownloadState.value = BulkDownloadState(completedFiles = 114, isDone = true)
                Log.i(TAG, "All surahs already downloaded.")
                return@launch
            }

            _bulkDownloadState.value = BulkDownloadState(
                totalFiles = 114,
                completedFiles = alreadyDownloaded.size,
                isRunning = true
            )
            Log.i(TAG, "Starting bulk download: ${toDownload.size} surahs remaining.")

            val semaphore = Semaphore(5)

            toDownload.map { surahNumber ->
                async {
                    semaphore.withPermit {
                        downloadSurahSync(surahNumber)
                        _bulkDownloadState.update { it.copy(completedFiles = it.completedFiles + 1) }
                        Log.i(TAG, "Surah $surahNumber done. Total: ${_bulkDownloadState.value.completedFiles}/114")
                    }
                }
            }.awaitAll()

            _bulkDownloadState.update { it.copy(isRunning = false, isDone = true) }
            Log.i(TAG, "Bulk download complete.")
        }
    }

    /**
     * Watchdog: runs every 60 s while a bulk download is active.
     * If no surah completes for 5 consecutive minutes, the stuck job is cancelled
     * and the download restarts from where it left off (already-downloaded surahs are skipped).
     */
    private fun startWatchdog() {
        scope.launch {
            var lastCompletedCount = _bulkDownloadState.value.completedFiles
            var lastProgressAt = System.currentTimeMillis()

            while (true) {
                delay(WATCHDOG_INTERVAL_MS)

                val state = _bulkDownloadState.value
                if (!state.isRunning) break

                if (state.completedFiles > lastCompletedCount) {
                    // Progress made — reset the stall timer
                    lastCompletedCount = state.completedFiles
                    lastProgressAt = System.currentTimeMillis()
                    Log.d(TAG, "Watchdog: progress OK — ${state.completedFiles}/114 complete.")
                } else {
                    val stalledMs = System.currentTimeMillis() - lastProgressAt
                    Log.w(TAG, "Watchdog: no progress for ${stalledMs / 1000}s (threshold ${STALL_THRESHOLD_MS / 1000}s).")

                    if (stalledMs >= STALL_THRESHOLD_MS) {
                        Log.w(TAG, "Watchdog: stall detected — cancelling and restarting download.")
                        bulkJob?.cancelAndJoin()
                        _bulkDownloadState.value = BulkDownloadState()
                        delay(1_000) // brief pause before restarting
                        startBulkDownload()
                        // Reset tracker for the new run
                        lastCompletedCount = _bulkDownloadState.value.completedFiles
                        lastProgressAt = System.currentTimeMillis()
                    }
                }
            }
            Log.i(TAG, "Watchdog exiting — downloads finished.")
        }
    }

    /**
     * Phase 1: Download MP3 → register in DB immediately (playable offline now).
     * Phase 2: Background transcode → AAC 64 kbps → swap DB path → delete MP3.
     */
    private suspend fun downloadSurahSync(surahNumber: Int) {
        updateProgress(surahNumber, DownloadProgress(surahNumber = surahNumber))
        Log.d(TAG, "Starting download: surah $surahNumber")

        try {
            val url = ReciterConfig.getAudioUrl(surahNumber)
            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()

            if (!response.isSuccessful) {
                val err = "HTTP ${response.code}"
                Log.e(TAG, "Surah $surahNumber failed: $err")
                updateProgress(surahNumber, DownloadProgress(surahNumber = surahNumber, error = err))
                return
            }

            val body = response.body ?: run {
                Log.e(TAG, "Surah $surahNumber: empty response body")
                updateProgress(surahNumber, DownloadProgress(surahNumber = surahNumber, error = "Empty response"))
                return
            }

            val totalBytes = body.contentLength()
            val audioDir = getAudioDirectory()
            val padded = surahNumber.toString().padStart(3, '0')
            val tempMp3 = File(audioDir, "${padded}_tmp.mp3")
            val mp3File = File(audioDir, "${padded}.mp3")

            var bytesRead = 0L
            body.byteStream().use { input ->
                tempMp3.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        val p = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                        updateProgress(surahNumber, DownloadProgress(
                            surahNumber = surahNumber,
                            bytesDownloaded = bytesRead,
                            totalBytes = totalBytes,
                            progress = p
                        ))
                    }
                }
            }
            tempMp3.renameTo(mp3File)
            Log.d(TAG, "Surah $surahNumber downloaded (${mp3File.length() / 1024} KB)")

            // Register MP3 immediately — surah is now playable offline
            downloadDao.insert(DownloadEntity(
                surahNumber = surahNumber,
                filePath = mp3File.absolutePath,
                fileSize = mp3File.length(),
                downloadedAt = System.currentTimeMillis()
            ))
            updateProgress(surahNumber, DownloadProgress(
                surahNumber = surahNumber,
                bytesDownloaded = bytesRead,
                totalBytes = totalBytes,
                progress = 1f,
                isComplete = true
            ))

            // Background transcode MP3 → AAC 64 kbps (non-blocking)
            scope.launch {
                transcodeSemaphore.withPermit {
                    Log.d(TAG, "Transcoding surah $surahNumber...")
                    val m4aFile = File(audioDir, "${padded}.m4a")
                    if (AudioTranscoder.transcodeToAac(mp3File, m4aFile)) {
                        downloadDao.insert(DownloadEntity(
                            surahNumber = surahNumber,
                            filePath = m4aFile.absolutePath,
                            fileSize = m4aFile.length(),
                            downloadedAt = System.currentTimeMillis()
                        ))
                        mp3File.delete()
                        Log.d(TAG, "Surah $surahNumber transcoded (${m4aFile.length() / 1024} KB)")
                    } else {
                        Log.w(TAG, "Surah $surahNumber transcode failed — keeping MP3")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Surah $surahNumber exception: ${e.message}")
            updateProgress(surahNumber, DownloadProgress(
                surahNumber = surahNumber, error = e.message ?: "Unknown error"
            ))
        }
    }

    suspend fun deleteDownload(surahNumber: Int) {
        val download = downloadDao.getDownload(surahNumber) ?: return
        File(download.filePath).delete()
        downloadDao.delete(surahNumber)
    }

    fun getTotalSize() = downloadDao.getTotalSize()
    fun getAllDownloads() = downloadDao.getAllDownloads()

    private fun getAudioDirectory(): File {
        val dir = File(context.getExternalFilesDir("audio"), "sds")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun updateProgress(surahNumber: Int, progress: DownloadProgress) {
        _downloadProgress.update { current ->
            current.toMutableMap().also { it[surahNumber] = progress }
        }
    }
}
