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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<PendingAppAction?>(null) }

    LaunchedEffect(includeSystemApps) {
        viewModel.load(includeSystemApps)
    }

    pendingAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text(actionTitle(pending.action)) },
            text = {
                Text(
                    "${pending.app.label}\n${pending.app.packageName}\n" +
                        "Режим: ${state.actionBackend.label}\n\n" +
                        actionDescription(pending.action),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.executeAction(pending.app, pending.action)
                        pendingAction = null
                    },
                ) {
                    Text("Выполнить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Отмена")
                }
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
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Приложения", style = MaterialTheme.typography.headlineMedium)
            Text(
                when (state.actionBackend) {
                    AppActionBackend.ROOT ->
                        "Активен Root. Расширенные действия доступны только для незащищённых пользовательских приложений."
                    AppActionBackend.SHIZUKU ->
                        if (state.shizukuCacheClearSupported) {
                            "Активен Shizuku. Доступны безопасная очистка кэша, остановка и заморозка."
                        } else {
                            "Активен Shizuku. На этой версии Android доступны остановка и заморозка; cache-only требует Android 13+."
                        }
                    AppActionBackend.NONE ->
                        "Размер кэша доступен после выдачи Usage Access. Расширенный режим можно включить в разделе доступа."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text("Поиск") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                singleLine = true,
            )
        }
        state.message?.let { message ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        state.error?.let { error ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        items(state.visibleApps, key = { it.packageName }) { app ->
            val protected = app.packageName in state.protectedPackages
            val busy = state.busyPackage == app.packageName
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(app.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val total = listOfNotNull(
                            app.appBytes,
                            app.dataBytes,
                            app.cacheBytes,
                        ).sum().takeIf { it > 0 } ?: app.apkBytes
                        Text("Версия ${app.versionName} · ${ByteFormatter.format(total)}")
                        app.cacheBytes?.let { cacheBytes ->
                            Text(
                                "Кэш: ${ByteFormatter.format(cacheBytes)}",
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (app.isSystem) {
                            Text("Системное", style = MaterialTheme.typography.labelSmall)
                        }
                        if (!app.isEnabled) {
                            Text("Заморожено", color = MaterialTheme.colorScheme.error)
                        }
                        if (protected) {
                            Text("Защищено исключением", color = MaterialTheme.colorScheme.primary)
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
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_DELETE,
                                        Uri.parse("package:${app.packageName}"),
                                    ),
                                )
                            },
                            onSetProtected = { enabled ->
                                viewModel.setProtected(app.packageName, enabled)
                            },
                            onAction = { action ->
                                pendingAction = PendingAppAction(app, action)
                            },
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
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
    onAction: (AppMaintenanceAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "Действия")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Системные сведения") },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
            if (!app.isSystem) {
                DropdownMenuItem(
                    text = { Text("Удалить приложение") },
                    onClick = {
                        expanded = false
                        onUninstall()
                    },
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
                onClick = {
                    expanded = false
                    onSetProtected(!protected)
                },
            )
            if (backend != AppActionBackend.NONE && !app.isSystem && !protected) {
                val cacheActionEnabled =
                    backend == AppActionBackend.ROOT || shizukuCacheClearSupported
                DropdownMenuItem(
                    text = {
                        Text(
                            if (cacheActionEnabled) {
                                "Очистить кэш · ${backend.label}"
                            } else {
                                "Очистка кэша требует Android 13+"
                            },
                        )
                    },
                    enabled = cacheActionEnabled,
                    onClick = {
                        expanded = false
                        onAction(AppMaintenanceAction.CLEAR_CACHE)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Остановить · ${backend.label}") },
                    onClick = {
                        expanded = false
                        onAction(AppMaintenanceAction.FORCE_STOP)
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (app.isEnabled) {
                                "Заморозить · ${backend.label}"
                            } else {
                                "Разморозить · ${backend.label}"
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onAction(
                            if (app.isEnabled) {
                                AppMaintenanceAction.FREEZE
                            } else {
                                AppMaintenanceAction.UNFREEZE
                            },
                        )
                    },
                )
            }
        }
    }
}

private fun actionTitle(action: AppMaintenanceAction): String = when (action) {
    AppMaintenanceAction.CLEAR_CACHE -> "Очистить кэш?"
    AppMaintenanceAction.FORCE_STOP -> "Остановить приложение?"
    AppMaintenanceAction.FREEZE -> "Заморозить приложение?"
    AppMaintenanceAction.UNFREEZE -> "Разморозить приложение?"
}

private fun actionDescription(action: AppMaintenanceAction): String = when (action) {
    AppMaintenanceAction.CLEAR_CACHE ->
        "Будет очищен только кэш пакета. Пользовательские данные и настройки останутся на месте."
    AppMaintenanceAction.FORCE_STOP ->
        "Приложение перестанет работать до следующего ручного запуска или системного события."
    AppMaintenanceAction.FREEZE ->
        "Пакет будет отключён для текущего пользователя до ручной разморозки."
    AppMaintenanceAction.UNFREEZE ->
        "Пакет снова станет доступен для запуска."
}
