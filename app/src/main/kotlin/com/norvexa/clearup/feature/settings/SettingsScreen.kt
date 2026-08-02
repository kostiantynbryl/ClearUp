package com.norvexa.clearup.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.designsystem.InfoCard
import com.norvexa.clearup.core.theme.ThemeMode
import com.norvexa.clearup.data.settings.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    onAbout: () -> Unit,
) {
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = repository.defaultSettings)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
            Text("ClearUp by NORVEXA", color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.material3.Icon(Icons.Outlined.Palette, contentDescription = null)
                        Text("Тема", style = MaterialTheme.typography.titleMedium)
                    }
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { scope.launch { repository.setThemeMode(mode) } }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.themeMode == mode, onClick = { scope.launch { repository.setThemeMode(mode) } })
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Как в системе"
                                    ThemeMode.LIGHT -> "Светлая"
                                    ThemeMode.DARK -> "Тёмная"
                                    ThemeMode.AMOLED -> "AMOLED (чёрный фон)"
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SettingSwitch(
                        title = "Безопасный режим",
                        description = "Автоматически отмечать только файлы с низким риском",
                        checked = settings.safeMode,
                        onCheckedChange = { scope.launch { repository.setSafeMode(it) } },
                    )
                    SettingSwitch(
                        title = "Показывать системные приложения",
                        description = "Системные пакеты нельзя удалять обычным способом",
                        checked = settings.includeSystemApps,
                        onCheckedChange = { scope.launch { repository.setIncludeSystemApps(it) } },
                    )
                    Text("Крупный файл: от ${settings.largeFileThresholdMb} МБ")
                    Slider(
                        value = settings.largeFileThresholdMb.toFloat(),
                        onValueChange = { scope.launch { repository.setLargeFileThresholdMb(it.toInt()) } },
                        valueRange = 20f..1024f,
                        steps = 49,
                    )
                }
            }
        }
        item {
            InfoCard(
                title = "Usage Access",
                body = "Откройте системный экран, чтобы ClearUp мог показывать размер данных и кэша приложений.",
                icon = Icons.Outlined.AccessTime,
                modifier = Modifier.clickable { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
        }
        item {
            InfoCard(
                title = "О программе",
                body = "Версия, приватность, лицензии и дорожная карта",
                icon = Icons.Outlined.Info,
                modifier = Modifier.clickable(onClick = onAbout),
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
