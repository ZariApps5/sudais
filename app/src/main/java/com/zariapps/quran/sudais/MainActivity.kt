package com.zariapps.quran.sudais

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.navigation.compose.rememberNavController
import com.zariapps.quran.sudais.ui.navigation.AppNavigation
import com.zariapps.quran.sudais.ui.settings.SettingsViewModel
import com.zariapps.quran.sudais.ui.theme.QuranTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: DataStore<Preferences>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val isDarkMode by dataStore.data
                .map { it[SettingsViewModel.DARK_MODE_KEY] ?: false }
                .collectAsState(initial = false)

            QuranTheme(darkTheme = isDarkMode) {
                val navController = rememberNavController()
                AppNavigation(navController = navController)
            }
        }
    }
}
