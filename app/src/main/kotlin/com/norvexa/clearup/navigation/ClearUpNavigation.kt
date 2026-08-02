package com.norvexa.clearup.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.norvexa.clearup.feature.duplicates.DuplicatesScreen
import com.norvexa.clearup.feature.duplicates.DuplicatesViewModel
import com.norvexa.clearup.feature.exclusions.ExclusionsScreen
import com.norvexa.clearup.feature.history.HistoryScreen
import com.norvexa.clearup.feature.home.HomeScreen
import com.norvexa.clearup.feature.home.HomeViewModel
import com.norvexa.clearup.feature.privilege.PrivilegeScreen
import com.norvexa.clearup.feature.privilege.PrivilegeViewModel
import com.norvexa.clearup.feature.privilege.RootMaintenanceScreen
import com.norvexa.clearup.feature.privilege.RootMaintenanceViewModel
import com.norvexa.clearup.feature.scan.ScanScreen
import com.norvexa.clearup.feature.scan.ScanViewModel
import com.norvexa.clearup.feature.settings.SettingsScreen
import com.norvexa.clearup.feature.tools.ToolsScreen
import com.norvexa.clearup.feature.update.UpdateScreen
import com.norvexa.clearup.feature.update.UpdateViewModel

private enum class MainDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Главная", Icons.Outlined.Home),
    SCAN("scan", "Очистка", Icons.Outlined.AutoAwesome),
    TOOLS("tools", "Инструменты", Icons.Outlined.Build),
    APPS("apps", "Приложения", Icons.Outlined.Apps),
    SETTINGS("settings", "Настройки", Icons.Outlined.Settings),
}

@Composable
fun ClearUpNavigation(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = container.settingsRepository.defaultSettings,
    )
    val mainRoutes = MainDestination.entries.mapTo(hashSetOf()) { it.route }

    Scaffold(
        topBar = {
            BrandTopBar(
                title = if (currentRoute in mainRoutes) {
                    null
                } else {
                    when (currentRoute) {
                        "about" -> "О программе"
                        "analyzer" -> "Анализатор"
                        "duplicates" -> "Дубликаты"
                        "exclusions" -> "Исключения"
                        "history" -> "История"
                        "privileges" -> "Доступ"
                        "root-maintenance" -> "Root-обслуживание"
                        "update" -> "Обновление"
                        else -> null
                    }
                },
            )
        },
        bottomBar = {
            if (currentRoute in mainRoutes) {
                NavigationBar {
                    MainDestination.entries.forEach { destination ->
                        val selected = backStack?.destination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(MainDestination.HOME.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
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
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(container.storageRepository),
                )
                HomeScreen(
                    viewModel = viewModel,
                    onStartScan = { navController.navigate(MainDestination.SCAN.route) },
                )
            }
            composable(MainDestination.SCAN.route) {
                val viewModel: ScanViewModel = viewModel(
                    factory = ScanViewModel.Factory(
                        scanner = container.scannerEngine,
                        trash = container.trashManager,
                        exclusions = container.exclusionRepository,
                        history = container.historyStore,
                    ),
                )
                ScanScreen(
                    viewModel = viewModel,
                    largeFileThresholdMb = settings.largeFileThresholdMb,
                    preselectSafeItems = settings.safeMode,
                )
            }
            composable(MainDestination.TOOLS.route) {
                ToolsScreen(
                    onAnalyzer = { navController.navigate("analyzer") },
                    onDuplicates = { navController.navigate("duplicates") },
                    onExclusions = { navController.navigate("exclusions") },
                    onHistory = { navController.navigate("history") },
                    onPrivileges = { navController.navigate("privileges") },
                    onRootMaintenance = { navController.navigate("root-maintenance") },
                    onUpdate = { navController.navigate("update") },
                )
            }
            composable(MainDestination.APPS.route) {
                val viewModel: AppsViewModel = viewModel(
                    factory = AppsViewModel.Factory(
                        repository = container.appRepository,
                        privilegeManager = container.privilegeManager,
                        rootShell = container.rootShell,
                        exclusions = container.exclusionRepository,
                        history = container.historyStore,
                        ownPackageName = container.packageName,
                    ),
                )
                AppsScreen(
                    viewModel = viewModel,
                    includeSystemApps = settings.includeSystemApps,
                )
            }
            composable(MainDestination.SETTINGS.route) {
                SettingsScreen(
                    repository = container.settingsRepository,
                    scheduler = container.automationScheduler,
                    onAbout = { navController.navigate("about") },
                )
            }
            composable("analyzer") {
                val viewModel: AnalyzerViewModel = viewModel(
                    factory = AnalyzerViewModel.Factory(container.storageRepository),
                )
                AnalyzerScreen(viewModel)
            }
            composable("duplicates") {
                val viewModel: DuplicatesViewModel = viewModel(
                    factory = DuplicatesViewModel.Factory(
                        repository = container.duplicateRepository,
                        exclusions = container.exclusionRepository,
                        trashManager = container.trashManager,
                        historyStore = container.historyStore,
                    ),
                )
                DuplicatesScreen(viewModel)
            }
            composable("exclusions") {
                ExclusionsScreen(container.exclusionRepository)
            }
            composable("history") {
                HistoryScreen(container.historyStore)
            }
            composable("privileges") {
                val viewModel: PrivilegeViewModel = viewModel(
                    factory = PrivilegeViewModel.Factory(container.privilegeManager),
                )
                PrivilegeScreen(viewModel)
            }
            composable("root-maintenance") {
                val viewModel: RootMaintenanceViewModel = viewModel(
                    factory = RootMaintenanceViewModel.Factory(
                        repository = container.rootOrphanRepository,
                        exclusions = container.exclusionRepository,
                        history = container.historyStore,
                        auditStore = container.rootAuditStore,
                    ),
                )
                RootMaintenanceScreen(viewModel)
            }
            composable("update") {
                val viewModel: UpdateViewModel = viewModel(
                    factory = UpdateViewModel.Factory(
                        repository = container.updateRepository,
                        history = container.historyStore,
                    ),
                )
                UpdateScreen(viewModel)
            }
            composable("about") {
                AboutScreen()
            }
        }
    }
}
