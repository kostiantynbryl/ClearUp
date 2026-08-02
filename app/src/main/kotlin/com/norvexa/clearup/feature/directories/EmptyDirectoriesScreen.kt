package com.norvexa.clearup.feature.directories

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.norvexa.clearup.core.util.DateFormatter

@Composable
fun EmptyDirectoriesScreen(viewModel: EmptyDirectoriesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirmDelete by remember { mutableStateOf(false) }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refreshAccess()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить пустые каталоги?") },
            text = {
                Text(
                    "Выбрано: ${state.selected.size}. Перед удалением ClearUp повторно проверит путь, отсутствие символической ссылки и фактическую пустоту каждой папки.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelected()
                    },
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Пустые каталоги", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Проверяются только публичные папки общего накопителя. Корни, Android/, символические ссылки и недавно изменённые каталоги исключаются.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!state.accessAvailable) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Требуется доступ к файлам", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                "Для проверки публичных папок разрешите ClearUp управление всеми файлами."
                            } else {
                                "Разрешите доступ к общему накопителю."
                            },
                        )
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                    runCatching { context.startActivity(intent) }
                                        .onFailure {
                                            context.startActivity(
                                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                                            )
                                        }
                                } else {
                                    legacyPermissionLauncher.launch(
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Открыть настройки доступа")
                        }
                    }
                }
            }
        }

        item {
            Text("Минимальный возраст", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EmptyDirectoriesViewModel.ALLOWED_MIN_AGE_DAYS.sorted().forEach { days ->
                    FilterChip(
                        selected = state.minAgeDays == days,
                        onClick = { viewModel.setMinAgeDays(days) },
                        label = { Text("$days дн.") },
                        enabled = !state.scanning && !state.deleting,
                    )
                }
            }
        }

        item {
            Button(
                onClick = viewModel::scan,
                enabled = state.accessAvailable && !state.scanning && !state.deleting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.scanning) {
                    CircularProgressIndicator()
                } else {
                    Text("Найти пустые каталоги")
                }
            }
        }

        state.message?.let { message ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        message,
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
                        error,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (state.directories.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Найдено: ${state.directories.size} · выбрано: ${state.selected.size}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = viewModel::selectAll) {
                        Text(
                            if (state.selectedPaths.size == state.directories.size) {
                                "Снять всё"
                            } else {
                                "Выбрать всё"
                            },
                        )
                    }
                }
            }
        }

        items(state.directories, key = { it.canonicalPath }) { directory ->
            val selected = directory.canonicalPath in state.selectedPaths
            Card(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { viewModel.toggle(directory.canonicalPath) },
                        enabled = !state.scanning && !state.deleting,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            directory.path.substringAfterLast('/').ifBlank { directory.path },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            directory.path,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Изменён: ${DateFormatter.format(directory.modifiedAtMillis)} · глубина ${directory.depth}",
                        )
                    }
                }
            }
        }

        if (state.selected.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.scanning && !state.deleting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.deleting) {
                        CircularProgressIndicator()
                    } else {
                        Text("Удалить выбранные (${state.selected.size})")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}
