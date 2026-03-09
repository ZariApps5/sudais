package com.zariapps.quran.sudais.ui.initialdownload

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zariapps.quran.sudais.config.ReciterConfig
import com.zariapps.quran.sudais.data.model.SurahData
import com.zariapps.quran.sudais.download.SurahProcessingStep

@Composable
fun InitialDownloadScreen(
    onReady: () -> Unit,
    viewModel: InitialDownloadViewModel = hiltViewModel()
) {
    val bulkState by viewModel.bulkDownloadState.collectAsState()
    val alreadyComplete by viewModel.alreadyComplete.collectAsState()

    // Navigate immediately if all files were already present
    LaunchedEffect(alreadyComplete) {
        if (alreadyComplete) onReady()
    }

    // Navigate when bulk download finishes
    LaunchedEffect(bulkState.isDone) {
        if (bulkState.isDone) onReady()
    }

    val overallProgress = if (bulkState.totalFiles > 0)
        bulkState.completedFiles.toFloat() / bulkState.totalFiles
    else 0f

    val currentSurahName = bulkState.currentSurahNumber?.let { num ->
        SurahData.allSurahs.find { it.number == num }?.nameEnglish
    }
    val isTranscoding = bulkState.currentStep == SurahProcessingStep.TRANSCODING

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = ReciterConfig.APP_NAME,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Text(
                text = ReciterConfig.RECITER_NAME_ARABIC,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Downloading Quran for offline use",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This only happens once. The app will work fully offline after this.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Overall progress bar
            LinearProgressIndicator(
                progress = overallProgress,
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${bulkState.completedFiles} / ${bulkState.totalFiles} surahs",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (currentSurahName != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val statusText = if (isTranscoding)
                    "Optimizing: $currentSurahName"
                else
                    "Downloading: $currentSurahName"
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            } else if (bulkState.isRunning) {
                Spacer(modifier = Modifier.height(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Allow entering the app while download continues in background
            if (bulkState.isRunning) {
                TextButton(onClick = onReady) {
                    Text("Continue to app while downloading")
                }
            }
        }
    }
}
