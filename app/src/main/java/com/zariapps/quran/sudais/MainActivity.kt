package com.zariapps.quran.sudais

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.zariapps.quran.sudais.player.PlayerManager
import com.zariapps.quran.sudais.ui.ads.AppOpenAdManager
import com.zariapps.quran.sudais.ui.ads.BannerAdView
import com.zariapps.quran.sudais.ui.navigation.AppNavigation
import com.zariapps.quran.sudais.ui.review.InAppReviewManager
import com.zariapps.quran.sudais.ui.settings.SettingsViewModel
import com.zariapps.quran.sudais.ui.theme.QuranTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    @Inject
    lateinit var appOpenAdManager: AppOpenAdManager

    @Inject
    lateinit var playerManager: PlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appOpenAdManager.load(this)

        setContent {
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(1500)
                if (!playerManager.isPlaying.value) {
                    appOpenAdManager.showWhenReady(this@MainActivity)
                }
                showSplash = false
                maybeRequestReview()
            }

            if (showSplash) {
                Image(
                    painter = painterResource(id = R.drawable.splash_image),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val isDarkMode by dataStore.data
                    .map { it[SettingsViewModel.DARK_MODE_KEY] ?: false }
                    .collectAsState(initial = false)

                QuranTheme(darkTheme = isDarkMode) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        AppNavigation(
                            navController = navController,
                            modifier = Modifier.weight(1f)
                        )
                        BannerAdView(modifier = Modifier.fillMaxWidth().navigationBarsPadding())
                    }
                }
            }
        }
    }

    private fun maybeRequestReview() {
        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            val count = (prefs[LAUNCH_COUNT_KEY] ?: 0) + 1
            val alreadyRequested = prefs[REVIEW_REQUESTED_KEY] ?: false
            dataStore.edit { it[LAUNCH_COUNT_KEY] = count }

            if (!alreadyRequested && count >= REVIEW_PROMPT_AFTER_LAUNCHES) {
                dataStore.edit { it[REVIEW_REQUESTED_KEY] = true }
                InAppReviewManager.requestReview(this@MainActivity)
            }
        }
    }

    companion object {
        private val LAUNCH_COUNT_KEY = intPreferencesKey("launch_count")
        private val REVIEW_REQUESTED_KEY = booleanPreferencesKey("review_requested")
        private const val REVIEW_PROMPT_AFTER_LAUNCHES = 3
    }
}
