package com.norvexa.clearup.feature.apps

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.util.ByteFormatter
import com.norvexa.clearup.domain.model.InstalledApp

private data class PendingAppAction(
    val app: InstalledApp,
    val action: AppMaintenanceAction,
)

@Composable
fun AppsScreen(
    viewModel: AppsViewModel,
    includeSystemApps: Boolean,
    onAccessibilitySetup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingAction by remember { mutableStateOf<PendingAppAction?>(null) }
    var confirmBatch by remember { mutableStateOf(false) }

    LaunchedEffect(includeSystemApps) {
        viewModel.load(includeSystemApps)
    }

    LaunchedEffect(state.accessibilityLaunchPackage) {
        val packageName = state.accessibilityLaunchPackage ?: return@LaunchedEffect
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"),
                ),
            )
        }.onSuccess {
            viewModel.consumeAccessibilityLaunch()
        }.onFailure { error ->
            viewModel.accessibilityLaunchFailed(
                error.message ?: "Система не открыла карточку выбранного приложения",
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibilityState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (confirmBatch) {
        AlertDialog(
            onDismissRequest = { confirmBatch = false },
            icon = { Icon(Icons.Outlined.CleaningServices, contentDescription = null) },
            title = { Text("Очистить скрытый кэш?") },
            text = {
                Text(
                    "Выбрано ${state.selectedApps.size} приложений. Режим: ${state.actionBackend.label}. " +
                        "ClearUp очищает только cache/code_cache и никогда не нажимает «Очистить данные».",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmBatch = false
                        viewModel.clearSelectedCaches()
                    },
                ) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmBatch = false }) { Text("Отмена") }
            },
        )
    }

    pendingAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(actionTitle(pending.action)) },
            text = {
                Text(
                    "${pending.app.label}\n${pending.app.packageName}\n" +
                        "Режим: ${state.actionBackend.label}\n\n" +
                        actionDescription(pending.action, state.actionBackend),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.executeAction(pending.app, pending.action)
                        pendingAction = null
                    },
                ) { Text("Выполнить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("Отмена") }
            },
        )
    }

    if (state.loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Text("Загружаем приложения…", modifier = Modifier.padding(top = 12.dp))
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Кэш приложений", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Выберите приложения один раз — ClearUp очистит их кэш по очереди.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Активный способ", style = MaterialTheme.typography.labelLarge)
                    Text(state.actionBackend.label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        backendDescription(state),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.actionBackend == AppActionBackend.NONE) {
                        OutlinedButton(
                            onClick = onAccessibilitySetup,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        ) { Text("Настроить Спецвозможности") }
                    }
                }
            }

            if (state.allApps.isNotEmpty() && state.allApps.all { it.cacheBytes == null }) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Показать размеры кэша")
                }
                Text(
                    "Без Usage Access очистка работает, но Android не сообщает размер кэша каждого приложения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Поиск приложений") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !state.batchRunning,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::selectVisibleEligible,
                    modifier = Modifier.weight(1f),
                    enabled = !state.batchRunning,
                ) { Text("Выбрать все") }
                OutlinedButton(
                    onClick = viewModel::clearSelection,
                    modifier = Modifier.weight(1f),
                    enabled = state.selectedPackages.isNotEmpty() && !state.batchRunning,
                ) { Text("Снять") }
            }
        }

        if (state.batchRunning) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val progress = if (state.batchTotal > 0) {
                    state.batchCompleted.toFloat() / state.batchTotal.toFloat()
                } else {
                    0f
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${state.batchCompleted} из ${state.batchTotal}" +
                        if (state.batchFailed > 0) " · ошибок ${state.batchFailed}" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
                state.batchMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        state.error?.let { error ->
            Text(
                text = error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.visibleApps, key = { it.packageName }) { app ->
                val eligible = state.isCacheEligible(app)
                val selected = app.packageName in state.selectedPackages
                val protected = app.packageName in state.protectedPackages
                val busy = state.busyPackage == app.packageName
                Card(
                    onClick = { if (eligible) viewModel.toggleSelected(app.packageName) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = if (eligible && !state.batchRunning) {
                                { viewModel.toggleSelected(app.packageName) }
                            } else {
                                null
                            },
                            enabled = eligible && !state.batchRunning,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(app.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                app.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                app.cacheBytes?.let { "Кэш ${ByteFormatter.format(it)}" }
                                    ?: "Размер кэша неизвестен",
                                color = if ((app.cacheBytes ?: 0L) > 0L) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            when {
                                app.isSystem -> Text("Системное · пакетная очистка отключена", style = MaterialTheme.typography.labelSmall)
                                protected -> Text("В исключениях", color = MaterialTheme.colorScheme.primary)
                                !app.isEnabled -> Text("Приложение отключено", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (busy) {
                            CircularProgressIndicator()
                        } else {
                            AppActionsMenu(
                                app = app,
                                backend = state.actionBackend,
                                shizukuCacheClearSupported = state.shizukuCacheClearSupported,
                                protected = protected,
                                protectionLocked = app.packageName == state.ownPackageName,
                                onOpenSettings = {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:${app.packageName}"),
                                        ),
                                    )
                                },
                                onUninstall = {
                                    context.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                                },
                                onSetProtected = { enabled -> viewModel.setProtected(app.packageName, enabled) },
                                onAccessibilitySetup = onAccessibilitySetup,
                                onAction = { action -> pendingAction = PendingAppAction(app, action) },
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        Button(
            onClick = { confirmBatch = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(18.dp),
            enabled = state.selectedApps.isNotEmpty() &&
                !state.batchRunning &&
                state.actionBackend != AppActionBackend.NONE &&
                !(state.actionBackend == AppActionBackend.SHIZUKU && !state.shizukuCacheClearSupported),
        ) {
            val sizeText = state.selectedCacheBytes.takeIf { it > 0 }?.let { " · ${ByteFormatter.format(it)}" }.orEmpty()
            Text("Очистить кэш · ${state.selectedApps.size}$sizeText")
        }
    }
}

@Composable
private fun AppActionsMenu(
    app: InstalledApp,
    backend: AppActionBackend,
    shizukuCacheClearSupported: Boolean,
    protected: Boolean,
    protectionLocked: Boolean,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit,
    onSetProtected: (Boolean) -> Unit,
    onAccessibilitySetup: () -> Unit,
    onAction: (AppMaintenanceAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Дополнительные действия")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Системные сведения") },
                onClick = { expanded = false; onOpenSettings() },
            )
            if (!app.isSystem) {
                DropdownMenuItem(
                    text = { Text("Удалить приложение") },
                    onClick = { expanded = false; onUninstall() },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        when {
                            protectionLocked -> "ClearUp защищён"
                            protected -> "Убрать из исключений"
                            else -> "Защитить исключением"
                        },
                    )
                },
                enabled = !protectionLocked,
                onClick = { expanded = false; onSetProtected(!protected) },
            )
            if (backend == AppActionBackend.NONE && !app.isSystem && !protected) {
                DropdownMenuItem(
                    text = { Text("Настроить Спецвозможности") },
                    onClick = { expanded = false; onAccessibilitySetup() },
                )
            }
            if (backend != AppActionBackend.NONE && !app.isSystem && !protected) {
                val cacheEnabled = backend != AppActionBackend.SHIZUKU || shizukuCacheClearSupported
                DropdownMenuItem(
                    text = { Text("Очистить кэш · ${backend.label}") },
                    enabled = cacheEnabled,
                    onClick = { expanded = false; onAction(AppMaintenanceAction.CLEAR_CACHE) },
                )
                if (backend == AppActionBackend.ROOT || backend == AppActionBackend.SHIZUKU) {
                    DropdownMenuItem(
                        text = { Text("Остановить") },
                        onClick = { expanded = false; onAction(AppMaintenanceAction.FORCE_STOP) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (app.isEnabled) "Заморозить" else "Разморозить") },
                        onClick = {
                            expanded = false
                            onAction(if (app.isEnabled) AppMaintenanceAction.FREEZE else AppMaintenanceAction.UNFREEZE)
                        },
                    )
                }
            }
        }
    }
}

private fun backendDescription(state: AppsUiState): String = when (state.actionBackend) {
    AppActionBackend.ROOT -> "Самый быстрый режим. Очищается только cache/code_cache выбранных пользовательских приложений."
    AppActionBackend.SHIZUKU -> if (state.shizukuCacheClearSupported) {
        "Android выполняет cache-only команду через Shizuku. Пользовательские данные не стираются."
    } else {
        "На этой версии Android безопасная Shizuku cache-only очистка недоступна."
    }
    AppActionBackend.ACCESSIBILITY -> "ClearUp по очереди открывает системные карточки только выбранных приложений и нажимает точную кнопку «Очистить кэш»."
    AppActionBackend.NONE -> "Для скрытого кэша нужен Root, Shizuku или один раз настроенный Accessibility-помощник."
}

private fun actionTitle(action: AppMaintenanceAction): String = when (action) {
    AppMaintenanceAction.CLEAR_CACHE -> "Очистить кэш?"
    AppMaintenanceAction.FORCE_STOP -> "Остановить приложение?"
    AppMaintenanceAction.FREEZE -> "Заморозить приложение?"
    AppMaintenanceAction.UNFREEZE -> "Разморозить приложение?"
}

private fun actionDescription(action: AppMaintenanceAction, backend: AppActionBackend): String = when (action) {
    AppMaintenanceAction.CLEAR_CACHE -> "Удаляется только кэш. Данные аккаунта и настройки приложения сохраняются."
    AppMaintenanceAction.FORCE_STOP -> "Android остановит процессы приложения до следующего запуска."
    AppMaintenanceAction.FREEZE -> "Приложение будет отключено для текущего пользователя."
    AppMaintenanceAction.UNFREEZE -> "Приложение снова станет доступно для текущего пользователя."
} + "\nBackend: ${backend.label}"
