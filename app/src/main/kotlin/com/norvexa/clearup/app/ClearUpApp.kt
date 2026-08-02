package com.norvexa.clearup.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.theme.ClearUpTheme
import com.norvexa.clearup.navigation.ClearUpNavigation

@Composable
fun ClearUpApp(container: AppContainer) {
    val settings = container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = container.settingsRepository.defaultSettings,
    ).value

    ClearUpTheme(themeMode = settings.themeMode) {
        ClearUpNavigation(container = container)
    }
}
