package com.norvexa.clearup.feature.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.designsystem.InfoCard
import com.norvexa.clearup.core.util.ByteFormatter

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartScan: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permissionRefresh by remember { mutableStateOf(0) }
    val mediaPermissions = remember {
        when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionRefresh++
    }
    val hasMediaPermission = remember(permissionRefresh) {
        mediaPermissions.all { ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    }
    val hasAllFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Память устройства", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Безопасный анализ и прозрачное удаление файлов",
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
            Button(onClick = onStartScan, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) {
                Text("Начать сканирование")
            }
        }
        if (!hasMediaPermission || !hasAllFiles) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Доступ к памяти", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "ClearUp анализирует файлы локально. Для полного результата нужны разрешения Android.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!hasMediaPermission) {
                            OutlinedButton(onClick = { permissionLauncher.launch(mediaPermissions) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Разрешить доступ к медиа")
                            }
                        }
                        if (!hasAllFiles && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Разрешить доступ ко всем файлам")
                            }
                        }
                    }
                }
            }
        }
        item {
            InfoCard(
                title = "Безопасный режим включён",
                body = "Пользовательские файлы не удаляются автоматически — сначала показывается список и системное подтверждение.",
                icon = Icons.Outlined.Security,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoCard(
                    title = "Локально",
                    body = "Без облака",
                    icon = Icons.Outlined.Storage,
                    modifier = Modifier.weight(1f),
                )
                InfoCard(
                    title = "Корзина",
                    body = "С восстановлением",
                    icon = Icons.Outlined.FolderDelete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun StorageHero(loading: Boolean, total: Long, used: Long, free: Long, fraction: Float) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    val primary = MaterialTheme.colorScheme.primary
                    val track = MaterialTheme.colorScheme.surfaceVariant
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 14.dp.toPx()
                        drawArc(track, -90f, 360f, false, Offset(stroke, stroke), Size(size.width - stroke * 2, size.height - stroke * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(primary, -90f, 360f * fraction, false, Offset(stroke, stroke), Size(size.width - stroke * 2, size.height - stroke * 2), style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.titleLarge)
                        Text("занято", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Всего: ${ByteFormatter.format(total)}", style = MaterialTheme.typography.titleMedium)
                Text("Занято: ${ByteFormatter.format(used)}")
                Text("Свободно: ${ByteFormatter.format(free)}", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
