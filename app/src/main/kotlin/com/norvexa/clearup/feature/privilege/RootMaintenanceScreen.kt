package com.norvexa.clearup.feature.privilege

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.util.ByteFormatter
import com.norvexa.clearup.core.util.DateFormatter

@Composable
fun RootMaintenanceScreen(viewModel: RootMaintenanceViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val auditEntries by viewModel.auditEntries.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить остатки приложений через Root?") },
            text = {
                Text(
                    "Выбрано ${state.selected.size} каталогов " +
                        "(${ByteFormatter.format(state.selectedBytes)}). " +
                        "Перед каждым удалением ClearUp повторно проверит, что пакет не установлен. " +
                        "Операцию нельзя отменить через системную корзину.",
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Root-обслуживание", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Проверяются только каталоги с корректным package name в заранее заданных областях. " +
                    "Установленные, защищённые и системные приложения не предлагаются для удаления.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = viewModel::scan,
                    enabled = !state.loading && !state.deleting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Проверить остатки")
                }
                OutlinedButton(
                    onClick = viewModel::clearSelection,
                    enabled = state.selected.isNotEmpty() && !state.deleting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Снять выбор")
                }
            }
        }
        if (state.loading || state.deleting) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            if (state.deleting) {
                                "Повторная проверка и удаление выбранных каталогов…"
                            } else {
                                "Чтение разрешённых Root-каталогов…"
                            },
                        )
                    }
                }
            }
        }
        state.error?.let { error ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        state.message?.let { message ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        if (state.candidates.isNotEmpty()) {
            item {
                Text(
                    "Найдено: ${state.candidates.size} · ${ByteFormatter.format(state.totalBytes)}",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            items(state.candidates, key = { it.path }) { candidate ->
                Card(
                    onClick = { viewModel.toggle(candidate.path) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Checkbox(
                            checked = candidate.selected,
                            onCheckedChange = { viewModel.toggle(candidate.path) },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                candidate.packageName,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(candidate.location.displayName)
                            Text(
                                candidate.path,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "${ByteFormatter.format(candidate.bytes)} · " +
                                    DateFormatter.format(candidate.modifiedAtMillis),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { confirmDelete = true },
                    enabled = state.selected.isNotEmpty() && !state.deleting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Удалить выбранные · ${ByteFormatter.format(state.selectedBytes)}")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Root-аудит", style = MaterialTheme.typography.titleLarge)
                if (auditEntries.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearAudit) {
                        Text("Очистить")
                    }
                }
            }
            Text(
                "Локально сохраняются тип операции, цель, код возврата и сокращённый вывод.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(auditEntries.take(50), key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        entry.action,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (entry.success) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                    Text(entry.target)
                    Text(
                        "Код ${entry.exitCode} · ${DateFormatter.format(entry.createdAtMillis)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val preview = entry.errorPreview.ifBlank { entry.outputPreview }
                    if (preview.isNotBlank()) {
                        Text(
                            preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                        )
                    }
                }
            }
        }
        if (auditEntries.isEmpty()) {
            item { Text("Root-операции ещё не выполнялись.") }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
