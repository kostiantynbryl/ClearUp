package com.norvexa.clearup.feature.scan

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.util.ByteFormatter
import com.norvexa.clearup.core.util.DateFormatter
import com.norvexa.clearup.data.cleanup.TrashAction
import com.norvexa.clearup.domain.model.RiskLevel
import kotlinx.coroutines.launch

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    largeFileThresholdMb: Int,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onTrashCompleted()
    }

    LaunchedEffect(Unit) {
        if (!state.scanning && state.scannedCount == 0 && state.items.isEmpty()) {
            viewModel.scan(largeFileThresholdMb)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { androidx.compose.material3.Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text("Переместить в корзину?") },
            text = { Text("Выбрано ${state.selectedItems.size} файлов (${ByteFormatter.format(state.selectedBytes)}). Android покажет системное подтверждение.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        when (val action = viewModel.prepareTrash()) {
                            is TrashAction.RequiresConfirmation -> trashLauncher.launch(IntentSenderRequest.Builder(action.pendingIntent).build())
                            is TrashAction.Completed -> viewModel.onDirectTrashResult(action)
                            TrashAction.NothingSelected -> Unit
                        }
                    }
                }) { Text("Продолжить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = viewModel::selectSafe, modifier = Modifier.weight(1f), enabled = !state.scanning) {
                Text("Только безопасные")
            }
            OutlinedButton(onClick = viewModel::clearSelection, modifier = Modifier.weight(1f), enabled = state.selectedItems.isNotEmpty()) {
                Text("Снять выбор")
            }
        }

        when {
            state.scanning -> {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Анализируем доступные файлы…")
                }
            }
            state.error != null -> {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.scan(largeFileThresholdMb) }) { Text("Повторить") }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text("Найдено ${state.items.size} объектов из ${state.scannedCount} проверенных", style = MaterialTheme.typography.titleMedium)
                        Text("Потенциально: ${ByteFormatter.format(state.totalBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.message?.let { Text(it, color = MaterialTheme.colorScheme.secondary) }
                    }
                    items(state.items, key = { it.uri }) { item ->
                        Card(onClick = { viewModel.toggle(item.uri) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Checkbox(checked = item.selected, onCheckedChange = { viewModel.toggle(item.uri) })
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(item.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text(item.category.displayName, color = when (item.riskLevel) {
                                        RiskLevel.SAFE -> MaterialTheme.colorScheme.secondary
                                        RiskLevel.REVIEW -> MaterialTheme.colorScheme.primary
                                        RiskLevel.CAUTION -> MaterialTheme.colorScheme.error
                                    })
                                    Text(item.path, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    Text("${ByteFormatter.format(item.bytes)} · ${DateFormatter.format(item.modifiedAtMillis)}", style = MaterialTheme.typography.bodySmall)
                                    Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
                Button(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    enabled = state.selectedItems.isNotEmpty(),
                ) {
                    Text("В корзину · ${ByteFormatter.format(state.selectedBytes)}")
                }
            }
        }
    }
}
