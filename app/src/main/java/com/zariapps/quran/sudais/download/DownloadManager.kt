package com.zariapps.quran.sudais.download

import android.content.Context
import com.zariapps.quran.sudais.audio.AudioTranscoder
import com.zariapps.quran.sudais.config.ReciterConfig
import com.zariapps.quran.sudais.data.local.DownloadDao
import com.zariapps.quran.sudais.data.local.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DownloadProgress(
    val surahNumber: Int,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = -1,
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val error: String? = null
)

enum class SurahProcessingStep { DOWNLOADING, TRANSCODING }

data class BulkDownloadState(
    val totalFiles: Int = 114,
    val completedFiles: Int = 0,
    val currentSurahNumber: Int? = null,
    val currentStep: SurahProcessingStep = SurahProcessingStep.DOWNLOADING,
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
    private val activeJobs = mutableMapOf<Int, Job>()

    private val _downloadProgress = MutableStateFlow<Map<Int, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val _bulkDownloadState = MutableStateFlow(BulkDownloadState())
    val bulkDownloadState: StateFlow<BulkDownloadState> = _bulkDownloadState.asStateFlow()

    suspend fun areAllDownloaded(): Boolean = downloadDao.count() >= 114

    /** Downloads all 114 surahs sequentially, skipping already-downloaded ones. */
    fun downloadAllSequentially() {
        if (_bulkDownloadState.value.isRunning) return

        scope.launch {
            val alreadyDownloaded = downloadDao.getDownloadedNumbersOnce().toSet()
            val toDownload = (1..114).filter { it !in alreadyDownloaded }

            if (toDownload.isEmpty()) {
                _bulkDownloadState.value = BulkDownloadState(completedFiles = 114, isDone = true)
                return@launch
            }

            _bulkDownloadState.value = BulkDownloadState(
                totalFiles = 114,
                completedFiles = alreadyDownloaded.size,
                isRunning = true
            )

            for (surahNumber in toDownload) {
                _bulkDownloadState.value = _bulkDownloadState.value.copy(
                    currentSurahNumber = surahNumber
                )
                downloadSurahSync(surahNumber)
                _bulkDownloadState.value = _bulkDownloadState.value.copy(
                    completedFiles = _bulkDownloadState.value.completedFiles + 1
                )
            }

            _bulkDownloadState.value = _bulkDownloadState.value.copy(
                isRunning = false,
                isDone = true,
                currentSurahNumber = null
            )
        }
    }

    /**
     * Downloads a single surah and transcodes it to 64 kbps AAC.
     * Falls back to keeping the raw MP3 if transcoding fails.
     */
    private suspend fun downloadSurahSync(surahNumber: Int) {
        val progress = DownloadProgress(surahNumber = surahNumber)
        updateProgress(surahNumber, progress)

        try {
            val url = ReciterConfig.getAudioUrl(surahNumber)
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                updateProgress(surahNumber, progress.copy(error = "Download failed: ${response.code}"))
                return
            }

            val body = response.body ?: run {
                updateProgress(surahNumber, progress.copy(error = "Empty response"))
                return
            }

            val totalBytes = body.contentLength()
            val audioDir = getAudioDirectory()
            val paddedNumber = surahNumber.toString().padStart(3, '0')
            val mp3File = File(audioDir, "${paddedNumber}_raw.mp3")
            val m4aFile = File(audioDir, "${paddedNumber}.m4a")

            // ── Phase 1: Download ────────────────────────────────────────────────────
            var bytesRead = 0L
            body.byteStream().use { input ->
                mp3File.outputStream().use { output ->
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

            // ── Phase 2: Transcode MP3 → AAC 64 kbps ────────────────────────────────
            _bulkDownloadState.value = _bulkDownloadState.value.copy(
                currentStep = SurahProcessingStep.TRANSCODING
            )

            val transcodedOk = AudioTranscoder.transcodeToAac(mp3File, m4aFile)
            val finalFile: File
            if (transcodedOk) {
                mp3File.delete() // remove the original MP3
                finalFile = m4aFile
            } else {
                // Transcoding failed — keep the MP3 as fallback
                m4aFile.delete()
                finalFile = File(audioDir, "${paddedNumber}.mp3")
                mp3File.renameTo(finalFile)
            }

            _bulkDownloadState.value = _bulkDownloadState.value.copy(
                currentStep = SurahProcessingStep.DOWNLOADING
            )

            downloadDao.insert(
                DownloadEntity(
                    surahNumber = surahNumber,
                    filePath = finalFile.absolutePath,
                    fileSize = finalFile.length(),
                    downloadedAt = System.currentTimeMillis()
                )
            )

            updateProgress(surahNumber, DownloadProgress(
                surahNumber = surahNumber,
                bytesDownloaded = bytesRead,
                totalBytes = totalBytes,
                progress = 1f,
                isComplete = true
            ))
        } catch (e: Exception) {
            updateProgress(surahNumber, progress.copy(error = e.message ?: "Unknown error"))
        }
    }

    /** Fire-and-forget download for individual surahs from the UI. */
    fun download(surahNumber: Int) {
        if (activeJobs.containsKey(surahNumber)) return
        val job = scope.launch {
            downloadSurahSync(surahNumber)
            activeJobs.remove(surahNumber)
        }
        activeJobs[surahNumber] = job
    }

    fun cancelDownload(surahNumber: Int) {
        activeJobs[surahNumber]?.cancel()
        activeJobs.remove(surahNumber)
        val current = _downloadProgress.value.toMutableMap()
        current.remove(surahNumber)
        _downloadProgress.value = current
    }

    suspend fun deleteDownload(surahNumber: Int) {
        val download = downloadDao.getDownload(surahNumber) ?: return
        File(download.filePath).delete()
        downloadDao.delete(surahNumber)
    }

    fun getLocalFilePath(surahNumber: Int): String {
        val audioDir = getAudioDirectory()
        return File(audioDir, "${surahNumber.toString().padStart(3, '0')}.mp3").absolutePath
    }

    fun getTotalSize() = downloadDao.getTotalSize()

    fun getAllDownloads() = downloadDao.getAllDownloads()

    private fun getAudioDirectory(): File {
        val dir = File(context.getExternalFilesDir("audio"), "sds")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun updateProgress(surahNumber: Int, progress: DownloadProgress) {
        val current = _downloadProgress.value.toMutableMap()
        current[surahNumber] = progress
        _downloadProgress.value = current
    }
}
