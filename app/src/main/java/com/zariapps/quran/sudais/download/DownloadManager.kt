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

    fun download(surahNumber: Int) {
        if (activeJobs.containsKey(surahNumber)) return

        val job = scope.launch {
            val progress = DownloadProgress(surahNumber = surahNumber)
            updateProgress(surahNumber, progress)

            try {
                val url = ReciterConfig.getAudioUrl(surahNumber)
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    updateProgress(surahNumber, progress.copy(error = "Download failed: ${response.code}"))
                    return@launch
                }

                val body = response.body ?: run {
                    updateProgress(surahNumber, progress.copy(error = "Empty response"))
                    return@launch
                }

                val totalBytes = body.contentLength()
                val audioDir = getAudioDirectory()
                val tempFile = File(audioDir, "${surahNumber}_temp.mp3")
                val finalFile = File(audioDir, "${surahNumber.toString().padStart(3, '0')}.mp3")

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
                updateProgress(surahNumber, progress.copy(error = e.message ?: "Unknown error"))
            } finally {
                activeJobs.remove(surahNumber)
            }
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
