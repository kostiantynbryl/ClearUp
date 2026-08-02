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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            if (event == Lifecycle.Event.ON_RESUME) {
                coordinator.refreshCapabilities()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Accessibility-помощник",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "Fallback для устройств без Root и Shizuku",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Что делает ClearUp", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "После вашего нажатия для конкретного приложения ClearUp открывает его системную карточку, переходит в раздел хранилища и нажимает только точную кнопку «Очистить кэш».",
                    )
                    Text(
                        "Сервис не читает содержимое приложений, не вводит текст, не выполняет жесты, не нажимает «Очистить данные/хранилище» и не запускает операции в фоне.",
                    )
                    Text(
                        "Каждый запрос действует не более 90 секунд и может быть отменён.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            StatusCard(
                title = "Системный сервис",
                value = if (state.serviceEnabled) "Включён" else "Выключен",
            )
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
                    "Я понимаю принцип работы и разрешаю использовать Accessibility только для выбранных мной операций очистки кэша.",
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
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.serviceEnabled) {
                        "Открыть настройки Accessibility"
                    } else {
                        "Согласен и открыть настройки"
                    },
                )
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
                    Text("Отозвать согласие и отменить запрос")
                }
            }
        }

        state.session?.let { session ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text("Последний запрос", style = MaterialTheme.typography.titleMedium)
                        Text(session.appLabel)
                        Text(
                            session.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(session.message)
                        Text(
                            when (session.stage) {
                                AccessibilityCacheStage.WAITING_APP_DETAILS -> "Ожидается карточка приложения"
                                AccessibilityCacheStage.WAITING_STORAGE_PAGE -> "Ожидается раздел хранилища"
                                AccessibilityCacheStage.COMPLETED -> "Команда выполнена"
                                AccessibilityCacheStage.FAILED -> "Запрос завершён ошибкой"
                                AccessibilityCacheStage.CANCELLED -> "Запрос отменён"
                            },
                            color = when (session.stage) {
                                AccessibilityCacheStage.COMPLETED -> MaterialTheme.colorScheme.primary
                                AccessibilityCacheStage.FAILED -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (session.active) {
                            OutlinedButton(
                                onClick = coordinator::cancel,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Отменить текущий запрос")
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
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
