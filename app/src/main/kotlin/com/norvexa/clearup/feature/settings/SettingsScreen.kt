package com.norvexa.clearup.feature.settings

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
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
import com.norvexa.clearup.automation.AutomationScheduler
import com.norvexa.clearup.core.designsystem.InfoCard
import com.norvexa.clearup.core.theme.ThemeMode
import com.norvexa.clearup.data.settings.SettingsRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    scheduler: AutomationScheduler,
    onAbout: () -> Unit,
) {
    val settings by repository.settings.collectAsStateWithLifecycle(
        initialValue = repository.defaultSettings,
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    fun updateSchedule(
        enabled: Boolean = settings.automaticScanEnabled,
        days: Int = settings.automaticScanIntervalDays,
        chargingOnly: Boolean = settings.automaticScanChargingOnly,
        thresholdMb: Int = settings.largeFileThresholdMb,
    ) {
        if (enabled) {
            scheduler.schedule(
                intervalDays = days,
                thresholdMb = thresholdMb,
                chargingOnly = chargingOnly,
            )
        } else {
            scheduler.cancel()
        }
    }

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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Outlined.Palette, contentDescription = null)
                        Text("Тема", style = MaterialTheme.typography.titleMedium)
                    }
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { repository.setThemeMode(mode) }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    scope.launch { repository.setThemeMode(mode) }
                                },
                            )
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SettingSwitch(
                        title = "Предварительно выбирать безопасные",
                        description = "После сканирования отмечать только файлы с низким риском",
                        checked = settings.safeMode,
                        onCheckedChange = { enabled ->
                            scope.launch { repository.setSafeMode(enabled) }
                        },
                    )
                    SettingSwitch(
                        title = "Показывать системные приложения",
                        description = "Системные пакеты нельзя удалять обычным способом",
                        checked = settings.includeSystemApps,
                        onCheckedChange = { enabled ->
                            scope.launch { repository.setIncludeSystemApps(enabled) }
                        },
                    )
                    Text("Крупный файл: от ${settings.largeFileThresholdMb} МБ")
                    Slider(
                        value = settings.largeFileThresholdMb.toFloat(),
                        onValueChange = { rawValue ->
                            val value = rawValue.toInt()
                            scope.launch {
                                repository.setLargeFileThresholdMb(value)
                                updateSchedule(thresholdMb = value)
                            }
                        },
                        valueRange = 20f..1024f,
                        steps = 49,
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Автоматизация", style = MaterialTheme.typography.titleLarge)
                    SettingSwitch(
                        title = "Периодическое сканирование",
                        description = "Только анализ и уведомление, без скрытого удаления",
                        checked = settings.automaticScanEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            }
                            scope.launch {
                                repository.setAutomaticScanEnabled(enabled)
                                updateSchedule(enabled = enabled)
                            }
                        },
                    )
                    Text("Интервал: ${settings.automaticScanIntervalDays} дн.")
                    Slider(
                        value = settings.automaticScanIntervalDays.toFloat(),
                        onValueChange = { rawValue ->
                            val days = rawValue.toInt()
                            scope.launch {
                                repository.setAutomaticScanIntervalDays(days)
                                updateSchedule(days = days)
                            }
                        },
                        valueRange = 1f..30f,
                        steps = 28,
                        enabled = settings.automaticScanEnabled,
                    )
                    SettingSwitch(
                        title = "Только во время зарядки",
                        description = "Снижает влияние фонового анализа на батарею",
                        checked = settings.automaticScanChargingOnly,
                        onCheckedChange = { chargingOnly ->
                            scope.launch {
                                repository.setAutomaticScanChargingOnly(chargingOnly)
                                updateSchedule(chargingOnly = chargingOnly)
                            }
                        },
                    )
                }
            }
        }
        item {
            InfoCard(
                title = "Usage Access",
                body = "Откройте системный экран, чтобы показывать размер данных и кэша приложений.",
                icon = Icons.Outlined.AccessTime,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
            )
        }
        item {
            InfoCard(
                title = "О программе",
                body = "Версия, приватность и дорожная карта",
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
