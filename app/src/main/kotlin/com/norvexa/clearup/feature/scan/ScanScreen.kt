package com.norvexa.clearup.feature.scan

import android.Manifest
import android.app.Activity
import android.os.Build
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.norvexa.clearup.core.util.DateFormatter
import com.norvexa.clearup.data.cleanup.TrashAction
import com.norvexa.clearup.data.storage.StorageAccessRepository
import com.norvexa.clearup.domain.model.CleanerCategory
import com.norvexa.clearup.domain.model.RiskLevel
import kotlinx.coroutines.launch

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    storageAccessRepository: StorageAccessRepository,
    largeFileThresholdMb: Int,
    preselectSafeItems: Boolean,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirmDelete by remember { mutableStateOf(false) }
    var accessState by remember { mutableStateOf(storageAccessRepository.readState()) }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        accessState = storageAccessRepository.readState()
    }
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        accessState = storageAccessRepository.readState()
    }
    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onTrashCompleted()
        } else {
            viewModel.onTrashCancelled()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessState = storageAccessRepository.readState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(accessState.completeAccess) {
        if (
            accessState.completeAccess &&
            !state.scanning &&
            state.scannedCount == 0 &&
            state.items.isEmpty()
        ) {
            viewModel.scan(largeFileThresholdMb, preselectSafeItems)
        }
    }

    if (!accessState.completeAccess) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
            Text("Разрешите доступ к файлам", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    "ClearUp нужен системный доступ «Ко всем файлам», чтобы видеть временные файлы, APK, скриншоты и другие объекты во всём общем хранилище. Без него сканирование не запускается и не показывает ложный результат «ничего не найдено»."
                } else {
                    "ClearUp нужен доступ на чтение общего хранилища. Удаление личных файлов всё равно потребует вашего подтверждения."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            allFilesLauncher.launch(storageAccessRepository.createAllFilesAccessIntent())
                        }.onFailure {
                            context.startActivity(storageAccessRepository.createAllFilesFallbackIntent())
                        }
                    } else {
                        legacyPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Открыть разрешение")
            }
            Text(
                "После возврата ClearUp сам перепроверит доступ и начнёт анализ.",
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Outlined.DeleteSweep, contentDescription = null) },
            title = { Text("Переместить в корзину?") },
            text = {
                Text(
                    "Выбрано ${state.selectedItems.size} файлов (${ByteFormatter.format(state.selectedBytes)}). Android покажет системное подтверждение. Скриншоты и другие REVIEW-файлы удаляются только если вы выбрали их вручную.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            when (val action = viewModel.prepareTrash()) {
                                is TrashAction.RequiresConfirmation -> trashLauncher.launch(
                                    IntentSenderRequest.Builder(action.pendingIntent).build(),
                                )
                                is TrashAction.Completed -> viewModel.onDirectTrashResult(action)
                                TrashAction.NothingSelected -> Unit
                            }
                        }
                    },
                ) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Очистка", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Безопасно освободить ${ByteFormatter.format(state.safeBytes)}",
                style = MaterialTheme.typography.titleLarge,
            )
            if (state.reviewCount > 0) {
                Text(
                    "Ещё ${state.reviewCount} объектов требуют ручной проверки. Они не считаются мусором.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = viewModel::selectSafe,
                    modifier = Modifier.weight(1f),
                    enabled = !state.scanning,
                ) { Text("Безопасные") }
                OutlinedButton(
                    onClick = viewModel::clearSelection,
                    modifier = Modifier.weight(1f),
                    enabled = state.selectedItems.isNotEmpty(),
                ) { Text("Снять выбор") }
            }
        }

        when {
            state.scanning -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Проверяем хранилище…")
            }

            state.error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { viewModel.scan(largeFileThresholdMb, preselectSafeItems) },
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Повторить") }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        state.message?.let {
                            Text(
                                it,
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    items(state.items, key = { it.uri }) { item ->
                        Card(
                            onClick = { viewModel.toggle(item.uri) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Checkbox(
                                    checked = item.selected,
                                    onCheckedChange = { viewModel.toggle(item.uri) },
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(item.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    val status = when {
                                        item.category == CleanerCategory.SCREENSHOTS -> "Скриншот · не мусор · только вручную"
                                        item.riskLevel == RiskLevel.SAFE -> "Безопасно для очистки"
                                        item.riskLevel == RiskLevel.REVIEW -> "Требует проверки"
                                        else -> "Осторожно · личный файл"
                                    }
                                    Text(
                                        status,
                                        color = when (item.riskLevel) {
                                            RiskLevel.SAFE -> MaterialTheme.colorScheme.secondary
                                            RiskLevel.REVIEW -> MaterialTheme.colorScheme.primary
                                            RiskLevel.CAUTION -> MaterialTheme.colorScheme.error
                                        },
                                    )
                                    Text(item.path, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                    Text(
                                        "${ByteFormatter.format(item.bytes)} · ${DateFormatter.format(item.modifiedAtMillis)}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        item.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    enabled = state.selectedItems.isNotEmpty(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("В корзину · ${ByteFormatter.format(state.selectedBytes)}")
                }
            }
        }
    }
}
