package com.zariapps.quran.sudais.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.zariapps.quran.sudais.ui.biography.BiographyScreen
import com.zariapps.quran.sudais.ui.home.HomeScreen
import com.zariapps.quran.sudais.ui.player.PlayerScreen
import com.zariapps.quran.sudais.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val BIOGRAPHY = "biography"
}

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToPlayer = { navController.navigate(Routes.PLAYER) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateToBiography = { navController.navigate(Routes.BIOGRAPHY) }
            )
        }
        composable(Routes.BIOGRAPHY) {
            BiographyScreen(onBack = { navController.popBackStack() })
        }
    }
}
