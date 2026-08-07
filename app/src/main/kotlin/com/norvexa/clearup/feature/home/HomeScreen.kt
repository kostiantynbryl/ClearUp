package com.norvexa.clearup.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.util.ByteFormatter
import com.norvexa.clearup.data.storage.StorageAccessRepository

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    storageAccessRepository: StorageAccessRepository,
    onStartScan: () -> Unit,
    onAppCache: () -> Unit,
    onTools: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessState = storageAccessRepository.readState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("ClearUp", style = MaterialTheme.typography.displaySmall)
            Text(
                "Хранилище без лишнего шума",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            StorageHero(
                loading = state.loading,
                total = state.summary.totalBytes,
                used = state.summary.usedBytes,
                free = state.summary.freeBytes,
                fraction = state.summary.usedFraction,
            )
        }
        item {
            Button(
                onClick = onStartScan,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !state.loading,
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                Text("Проверить и очистить", modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            FilledTonalButton(
                onClick = onAppCache,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.Outlined.Apps, contentDescription = null)
                Text("Очистить кэш приложений", modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (!accessState.completeAccess) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text("Доступ к файлам", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                "Для полного анализа включите системный доступ «Ко всем файлам». ClearUp не начнёт сканирование без него и не покажет ложный пустой результат."
                            } else {
                                "Разрешите чтение общего хранилища для полного анализа."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
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
                            Text("Настроить доступ")
                        }
                    }
                }
            }
        }

        item {
            Text("Ещё", style = MaterialTheme.typography.titleLarge)
        }
        item {
            Card(
                onClick = onTools,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Icon(Icons.Outlined.Build, contentDescription = null)
                    Column(Modifier.weight(1f)) {
                        Text("Инструменты", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Дубликаты, анализатор, пустые каталоги, история и расширенный доступ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageHero(
    loading: Boolean,
    total: Long,
    used: Long,
    free: Long,
    fraction: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(126.dp)) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    val primary = MaterialTheme.colorScheme.primary
                    val track = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 13.dp.toPx()
                        drawArc(
                            track,
                            -90f,
                            360f,
                            false,
                            Offset(stroke, stroke),
                            Size(size.width - stroke * 2, size.height - stroke * 2),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            primary,
                            -90f,
                            360f * fraction,
                            false,
                            Offset(stroke, stroke),
                            Size(size.width - stroke * 2, size.height - stroke * 2),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
                        Text("занято", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("${ByteFormatter.format(free)} свободно", style = MaterialTheme.typography.titleLarge)
                Text("Занято ${ByteFormatter.format(used)}")
                Text(
                    "Всего ${ByteFormatter.format(total)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
