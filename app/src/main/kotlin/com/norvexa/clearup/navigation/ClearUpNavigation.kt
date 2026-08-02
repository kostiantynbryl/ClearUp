package com.norvexa.clearup.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.norvexa.clearup.app.AppContainer
import com.norvexa.clearup.core.designsystem.BrandTopBar
import com.norvexa.clearup.feature.about.AboutScreen
import com.norvexa.clearup.feature.analyzer.AnalyzerScreen
import com.norvexa.clearup.feature.analyzer.AnalyzerViewModel
import com.norvexa.clearup.feature.apps.AppsScreen
import com.norvexa.clearup.feature.apps.AppsViewModel
import com.norvexa.clearup.feature.home.HomeScreen
import com.norvexa.clearup.feature.home.HomeViewModel
import com.norvexa.clearup.feature.scan.ScanScreen
import com.norvexa.clearup.feature.scan.ScanViewModel
import com.norvexa.clearup.feature.settings.SettingsScreen

private enum class MainDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    HOME("home", "Главная", Icons.Outlined.Home),
    SCAN("scan", "Очистка", Icons.Outlined.AutoAwesome),
    ANALYZER("analyzer", "Анализ", Icons.Outlined.Storage),
    APPS("apps", "Приложения", Icons.Outlined.Apps),
    SETTINGS("settings", "Настройки", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClearUpNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = container.settingsRepository.defaultSettings,
    )
    val mainRoutes = MainDestination.entries.map { it.route }.toSet()

    Scaffold(
        topBar = { BrandTopBar(title = if (currentRoute == "about") "О программе" else null) },
        bottomBar = {
            if (currentRoute in mainRoutes) {
                NavigationBar {
                    MainDestination.entries.forEach { destination ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(MainDestination.HOME.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(MainDestination.HOME.route) {
                val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container.storageRepository))
                HomeScreen(vm, onStartScan = { navController.navigate(MainDestination.SCAN.route) })
            }
            composable(MainDestination.SCAN.route) {
                val vm: ScanViewModel = viewModel(factory = ScanViewModel.Factory(container.scannerEngine, container.trashManager))
                ScanScreen(vm, largeFileThresholdMb = settings.largeFileThresholdMb)
            }
            composable(MainDestination.ANALYZER.route) {
                val vm: AnalyzerViewModel = viewModel(factory = AnalyzerViewModel.Factory(container.storageRepository))
                AnalyzerScreen(vm)
            }
            composable(MainDestination.APPS.route) {
                val vm: AppsViewModel = viewModel(factory = AppsViewModel.Factory(container.appRepository))
                AppsScreen(vm, includeSystemApps = settings.includeSystemApps)
            }
            composable(MainDestination.SETTINGS.route) {
                SettingsScreen(container.settingsRepository, onAbout = { navController.navigate("about") })
            }
            composable("about") { AboutScreen() }
        }
    }
}
