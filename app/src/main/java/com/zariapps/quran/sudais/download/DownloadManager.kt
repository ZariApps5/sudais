package com.zariapps.quran.sudais.download

import android.content.Context
import com.zariapps.quran.sudais.config.ReciterConfig
import com.zariapps.quran.sudais.data.local.DownloadDao
import com.zariapps.quran.sudais.data.local.DownloadEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    private val activeJobs = mutableMapOf<Int, Job>()

    private val _downloadProgress = MutableStateFlow<Map<Int, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<Int, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val _bulkDownloadState = MutableStateFlow(BulkDownloadState())
    val bulkDownloadState: StateFlow<BulkDownloadState> = _bulkDownloadState.asStateFlow()

    suspend fun areAllDownloaded(): Boolean = downloadDao.count() >= 114

    /**
     * Downloads all 114 surahs, 3 at a time, skipping already-downloaded ones.
     * Files are stored as MP3 — no transcoding, so each surah is ready to play
     * as soon as its download completes.
     */
    fun downloadAll() {
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

            // 3 concurrent downloads — balances speed vs. server politeness
            val semaphore = Semaphore(3)

            toDownload.map { surahNumber ->
                async {
                    semaphore.withPermit {
                        downloadSurahSync(surahNumber)
                        _bulkDownloadState.update { it.copy(completedFiles = it.completedFiles + 1) }
                    }
                }
            }.awaitAll()

            _bulkDownloadState.update { it.copy(isRunning = false, isDone = true) }
        }
    }

    /** Downloads a single surah and saves it as an MP3. */
    private suspend fun downloadSurahSync(surahNumber: Int) {
        updateProgress(surahNumber, DownloadProgress(surahNumber = surahNumber))

        try {
            val url = ReciterConfig.getAudioUrl(surahNumber)
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                updateProgress(surahNumber, DownloadProgress(
                    surahNumber = surahNumber,
                    error = "Download failed: ${response.code}"
                ))
                return
            }

            val body = response.body ?: run {
                updateProgress(surahNumber, DownloadProgress(surahNumber = surahNumber, error = "Empty response"))
                return
            }

            val totalBytes = body.contentLength()
            val audioDir = getAudioDirectory()
            val paddedNumber = surahNumber.toString().padStart(3, '0')
            val tempFile = File(audioDir, "${paddedNumber}_tmp.mp3")
            val finalFile = File(audioDir, "${paddedNumber}.mp3")

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
            updateProgress(surahNumber, DownloadProgress(
                surahNumber = surahNumber,
                error = e.message ?: "Unknown error"
            ))
        }
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
