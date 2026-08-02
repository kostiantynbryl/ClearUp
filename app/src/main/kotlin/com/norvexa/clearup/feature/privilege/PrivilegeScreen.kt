package com.norvexa.clearup.feature.privilege

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PrivilegeScreen(viewModel: PrivilegeViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Расширенный доступ", style = MaterialTheme.typography.headlineMedium)
        Text(
            "ClearUp работает без привилегий. Root и Shizuku включают дополнительные операции только после явного разрешения.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ui.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            StatusCard("Текущий режим", ui.state.bestMode.name)
            StatusCard("Root", if (ui.state.rootAvailable) "Доступен" else "Не найден")
            StatusCard(
                "Shizuku",
                when {
                    !ui.state.shizukuInstalled -> "Не установлен"
                    !ui.state.shizukuRunning -> "Установлен, но не запущен"
                    ui.state.shizukuPermissionGranted -> "Разрешён · UID ${ui.state.shizukuUid}"
                    else -> "Работает, требуется разрешение"
                },
            )
            if (ui.state.shizukuRunning && !ui.state.shizukuPermissionGranted) {
                Button(
                    onClick = viewModel::requestShizuku,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Разрешить Shizuku")
                }
            }
            OutlinedButton(
                onClick = viewModel::refresh,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Обновить статус")
            }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "Root-команды ограничены allowlist: очистка cache/code_cache, force-stop и заморозка выбранного пакета. Пользовательские данные и системные разделы не затрагиваются.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatusCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value)
        }
    }
}
