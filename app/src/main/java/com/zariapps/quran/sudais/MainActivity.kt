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
import androidx.navigation.compose.rememberNavController
import com.zariapps.quran.sudais.ui.ads.BannerAdView
import com.zariapps.quran.sudais.ui.navigation.AppNavigation
import com.zariapps.quran.sudais.ui.settings.SettingsViewModel
import com.zariapps.quran.sudais.ui.theme.QuranTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(1500)
                showSplash = false
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
}
