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
                    ui.state.shizukuVersion != null && ui.state.shizukuVersion < 11 ->
                        "Запущен, но API ${ui.state.shizukuVersion} не поддерживается"
                    ui.state.shizukuPermissionGranted ->
                        "Разрешён · API ${ui.state.shizukuVersion} · ${shizukuIdentity(ui.state.shizukuUid)}"
                    else -> "Работает · API ${ui.state.shizukuVersion ?: "?"} · требуется разрешение"
                },
            )
            if (
                ui.state.shizukuRunning &&
                (ui.state.shizukuVersion ?: 0) >= 11 &&
                !ui.state.shizukuPermissionGranted
            ) {
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
            "Автоматический приоритет: Root → Shizuku → стандартный режим. Через Shizuku доступны только очистка кэша, force-stop, заморозка и разморозка выбранного пользовательского пакета.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Root-команды ограничены allowlist: cache/code_cache, force-stop, заморозка и проверяемое удаление остатков. Пользовательские данные установленных приложений и системные разделы не затрагиваются.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun shizukuIdentity(uid: Int?): String = when (uid) {
    0 -> "Root/Sui"
    2000 -> "ADB shell"
    null -> "UID неизвестен"
    else -> "UID $uid"
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
