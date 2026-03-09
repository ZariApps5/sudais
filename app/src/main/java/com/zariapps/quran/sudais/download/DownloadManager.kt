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
private const val STALL_THRESHOLD_MS  = 5 * 60 * 1000L
private const val WATCHDOG_INTERVAL_MS = 60 * 1000L

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
    private var bulkJob: Job? = null

    // Max 2 simultaneous transcodings — keeps thermals stable while downloads run
    private val transcodeSemaphore = Semaphore(2)

    private val _downloadProgress = MutableStateFlow<Map<Int, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val _bulkDownloadState = MutableStateFlow(BulkDownloadState())
    val bulkDownloadState: StateFlow<BulkDownloadState> = _bulkDownloadState.asStateFlow()

    suspend fun areAllDownloaded(): Boolean = downloadDao.count() >= 114

    /**
     * Entry point called on every app launch.
     *  1. Downloads any missing surahs (5 at a time, shortest first).
     *  2. Resumes transcoding for any surahs still stored as MP3
     *     (handles the case where the app was killed mid-transcode on a previous run).
     */
    fun downloadAll() {
        if (_bulkDownloadState.value.isRunning) return
        startBulkDownload()
        startWatchdog()
        resumePendingTranscodes()  // pick up where we left off after a restart
    }

    // ── Download ────────────────────────────────────────────────────────────────

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
            Log.i(TAG, "Bulk download: ${toDownload.size} surahs remaining.")

            val semaphore = Semaphore(5)
            toDownload.map { surahNumber ->
                async {
                    semaphore.withPermit {
                        downloadSurahSync(surahNumber)
                        _bulkDownloadState.update { it.copy(completedFiles = it.completedFiles + 1) }
                        Log.i(TAG, "Surah $surahNumber done. ${_bulkDownloadState.value.completedFiles}/114")
                    }
                }
            }.awaitAll()

            _bulkDownloadState.update { it.copy(isRunning = false, isDone = true) }
            Log.i(TAG, "All downloads complete.")
        }
    }

    private fun startWatchdog() {
        scope.launch {
            var lastCount = _bulkDownloadState.value.completedFiles
            var lastProgressAt = System.currentTimeMillis()

            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                val state = _bulkDownloadState.value
                if (!state.isRunning) break

                if (state.completedFiles > lastCount) {
                    lastCount = state.completedFiles
                    lastProgressAt = System.currentTimeMillis()
                    Log.d(TAG, "Watchdog OK: ${state.completedFiles}/114")
                } else {
                    val stalledMs = System.currentTimeMillis() - lastProgressAt
                    Log.w(TAG, "Watchdog: no progress for ${stalledMs / 1000}s")
                    if (stalledMs >= STALL_THRESHOLD_MS) {
                        Log.w(TAG, "Watchdog: stall — restarting.")
                        bulkJob?.cancelAndJoin()
                        _bulkDownloadState.value = BulkDownloadState()
                        delay(1_000)
                        startBulkDownload()
                        lastCount = _bulkDownloadState.value.completedFiles
                        lastProgressAt = System.currentTimeMillis()
                    }
                }
            }
            Log.i(TAG, "Watchdog done.")
        }
    }

    private suspend fun downloadSurahSync(surahNumber: Int) {
        updateProgress(surahNumber, DownloadProgress(surahNumber = surahNumber))
        Log.d(TAG, "Downloading surah $surahNumber")

        try {
            val url      = ReciterConfig.getAudioUrl(surahNumber)
            val response = okHttpClient.newCall(Request.Builder().url(url).build()).execute()

            if (!response.isSuccessful) {
                val err = "HTTP ${response.code}"
                Log.e(TAG, "Surah $surahNumber: $err")
                updateProgress(surahNumber, DownloadProgress(surahNumber = surahNumber, error = err))
                return
            }

            val body = response.body ?: run {
                Log.e(TAG, "Surah $surahNumber: empty body")
                updateProgress(surahNumber, DownloadProgress(surahNumber = surahNumber, error = "Empty response"))
                return
            }

            val totalBytes = body.contentLength()
            val audioDir   = getAudioDirectory()
            val padded     = surahNumber.toString().padStart(3, '0')
            val tempFile   = File(audioDir, "${padded}_tmp.mp3")
            val finalFile  = File(audioDir, "${padded}.mp3")

            var bytesRead = 0L
            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
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
            tempFile.renameTo(finalFile)
            Log.d(TAG, "Surah $surahNumber: ${finalFile.length() / 1024} KB downloaded")

            // Register MP3 immediately — surah is playable offline right now
            downloadDao.insert(DownloadEntity(
                surahNumber  = surahNumber,
                filePath     = finalFile.absolutePath,
                fileSize     = finalFile.length(),
                downloadedAt = System.currentTimeMillis()
            ))
            updateProgress(surahNumber, DownloadProgress(
                surahNumber     = surahNumber,
                bytesDownloaded = bytesRead,
                totalBytes      = totalBytes,
                progress        = 1f,
                isComplete      = true
            ))

            // Queue background transcode (non-blocking)
            launchTranscode(surahNumber, finalFile, audioDir, padded)

        } catch (e: Exception) {
            Log.e(TAG, "Surah $surahNumber: ${e.message}")
            updateProgress(surahNumber, DownloadProgress(
                surahNumber = surahNumber, error = e.message ?: "Unknown error"
            ))
        }
    }

    // ── Transcoding ─────────────────────────────────────────────────────────────

    /**
     * On startup, finds any DB entries still pointing to .mp3 files and
     * re-queues them for transcoding. Handles the case where the app was
     * killed before a previous transcode session could finish.
     */
    private fun resumePendingTranscodes() {
        scope.launch {
            val allDownloads = downloadDao.getAllDownloadsOnce()
            val pending = allDownloads.filter { it.filePath.endsWith(".mp3") }
            if (pending.isEmpty()) {
                Log.i(TAG, "No pending MP3→AAC conversions.")
                return@launch
            }
            Log.i(TAG, "Resuming ${pending.size} pending transcodes.")
            val audioDir = getAudioDirectory()
            pending.forEach { entity ->
                val mp3 = File(entity.filePath)
                if (mp3.exists()) {
                    val padded = entity.surahNumber.toString().padStart(3, '0')
                    launchTranscode(entity.surahNumber, mp3, audioDir, padded)
                }
            }
        }
    }

    /**
     * Launches a background transcode for one surah. Uses the size guard:
     * only replaces the MP3 if the resulting AAC is genuinely smaller.
     */
    private fun launchTranscode(
        surahNumber: Int,
        mp3File: File,
        audioDir: File,
        padded: String
    ) {
        scope.launch {
            transcodeSemaphore.withPermit {
                val m4aFile   = File(audioDir, "${padded}.m4a")
                val mp3SizeKb = mp3File.length() / 1024
                val ok        = AudioTranscoder.transcodeToAac(mp3File, m4aFile)

                if (ok && m4aFile.length() < mp3File.length()) {
                    downloadDao.insert(DownloadEntity(
                        surahNumber  = surahNumber,
                        filePath     = m4aFile.absolutePath,
                        fileSize     = m4aFile.length(),
                        downloadedAt = System.currentTimeMillis()
                    ))
                    mp3File.delete()
                    Log.d(TAG, "Surah $surahNumber: ${mp3SizeKb}KB → ${m4aFile.length() / 1024}KB AAC ✓")
                } else {
                    m4aFile.delete()
                    Log.d(TAG, "Surah $surahNumber: kept ${mp3SizeKb}KB MP3 (transcode ${if (ok) "was larger" else "failed"})")
                }
            }
        }
    }

    // ── Misc ────────────────────────────────────────────────────────────────────

    suspend fun deleteDownload(surahNumber: Int) {
        val download = downloadDao.getDownload(surahNumber) ?: return
        File(download.filePath).delete()
        downloadDao.delete(surahNumber)
    }

    fun getTotalSize()    = downloadDao.getTotalSize()
    fun getAllDownloads()  = downloadDao.getAllDownloads()

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
