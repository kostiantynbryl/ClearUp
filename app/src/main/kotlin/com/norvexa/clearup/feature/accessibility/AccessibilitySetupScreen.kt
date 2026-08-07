package com.norvexa.clearup.feature.accessibility

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.data.accessibility.AccessibilityBatchStage
import com.norvexa.clearup.data.accessibility.AccessibilityCacheCoordinator
import com.norvexa.clearup.data.accessibility.AccessibilityCacheStage

@Composable
fun AccessibilitySetupScreen(coordinator: AccessibilityCacheCoordinator) {
    val state by coordinator.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var consentChecked by rememberSaveable { mutableStateOf(state.consentAccepted) }

    LaunchedEffect(state.consentAccepted) {
        consentChecked = state.consentAccepted
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) coordinator.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Очистка через Спецвозможности", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Режим для устройств без Root и подходящего Shizuku",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (state.serviceEnabled && state.consentAccepted) "Готово к работе" else "Нужна одноразовая настройка",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        if (state.serviceEnabled) "Системный сервис включён" else "Системный сервис выключен",
                        color = if (state.serviceEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "После запуска пакетной очистки ClearUp открывает только карточки выбранных приложений и нажимает только точную кнопку «Очистить кэш». Неизвестный экран останавливает очередь.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = consentChecked,
                    onCheckedChange = { consentChecked = it },
                )
                Text(
                    "Разрешаю ClearUp использовать Спецвозможности только для выбранной мной очистки кэша.",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Button(
                onClick = {
                    coordinator.setConsentAccepted(true)
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                enabled = consentChecked,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(if (state.serviceEnabled) "Открыть системные настройки" else "Продолжить в системных настройках")
            }
        }

        if (state.consentAccepted) {
            item {
                OutlinedButton(
                    onClick = {
                        consentChecked = false
                        coordinator.setConsentAccepted(false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Отозвать согласие")
                }
            }
        }

        state.batch?.let { batch ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("Пакетная очистка", style = MaterialTheme.typography.titleMedium)
                        Text("${batch.completedCount} из ${batch.totalCount} · ошибок ${batch.failedCount}")
                        batch.currentItem?.takeIf { batch.active }?.let { Text("Сейчас: ${it.appLabel}") }
                        Text(batch.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (batch.stage) {
                                AccessibilityBatchStage.RUNNING -> "Выполняется"
                                AccessibilityBatchStage.COMPLETED -> "Завершено"
                                AccessibilityBatchStage.FAILED -> "Остановлено безопасностью"
                                AccessibilityBatchStage.CANCELLED -> "Отменено"
                            },
                            color = when (batch.stage) {
                                AccessibilityBatchStage.COMPLETED -> MaterialTheme.colorScheme.secondary
                                AccessibilityBatchStage.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            },
                        )
                        if (batch.active) {
                            OutlinedButton(
                                onClick = { coordinator.cancel() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Остановить очередь") }
                        }
                    }
                }
            }
        } ?: state.session?.let { session ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Последний запрос", style = MaterialTheme.typography.titleMedium)
                        Text(session.appLabel)
                        Text(session.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            when (session.stage) {
                                AccessibilityCacheStage.WAITING_APP_DETAILS -> "Ожидается карточка приложения"
                                AccessibilityCacheStage.WAITING_STORAGE_PAGE -> "Ожидается раздел хранилища"
                                AccessibilityCacheStage.COMPLETED -> "Выполнено"
                                AccessibilityCacheStage.FAILED -> "Ошибка"
                                AccessibilityCacheStage.CANCELLED -> "Отменено"
                            },
                        )
                    }
                }
            }
        }

        item {
            Text(
                "ClearUp не выполняет жесты, не вводит текст, не нажимает «Очистить данные/хранилище» и не работает с произвольными приложениями без вашего выбора.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}
