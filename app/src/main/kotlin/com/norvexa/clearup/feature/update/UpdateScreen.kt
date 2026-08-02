package com.norvexa.clearup.feature.update

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.BuildConfig
import com.norvexa.clearup.core.designsystem.InfoCard

@Composable
fun UpdateScreen(viewModel: UpdateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val canInstall = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("Обновление ClearUp", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Установлено: ${BuildConfig.VERSION_NAME}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            InfoCard(
                title = "Проверка безопасности",
                body = "Требуются SHA-256, совпадающий package name и тот же сертификат подписи.",
                icon = Icons.Outlined.Security,
            )
        }
        item {
            when (val current = state) {
                UpdateUiState.Idle -> Button(
                    onClick = viewModel::check,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Проверить обновления")
                }

                UpdateUiState.Checking -> ProgressCard("Проверяем GitHub Releases…")
                UpdateUiState.UpToDate -> Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Установлена актуальная версия",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        OutlinedButton(onClick = viewModel::check) {
                            Text("Проверить снова")
                        }
                    }
                }

                is UpdateUiState.Available -> UpdateCard(
                    title = current.update.title,
                    tag = current.update.tag,
                    notes = current.update.notes,
                    actionText = "Скачать и проверить",
                    onAction = { viewModel.download(current.update) },
                )

                is UpdateUiState.Downloading -> ProgressCard(
                    "Загружаем и проверяем ${current.update.tag}…",
                )

                is UpdateUiState.Ready -> UpdateCard(
                    title = "APK проверен",
                    tag = current.update.tag,
                    notes = "SHA-256 и подпись совпадают. Установка выполняется системным установщиком Android.",
                    actionText = if (canInstall) {
                        "Установить обновление"
                    } else {
                        "Разрешить установку APK"
                    },
                    onAction = {
                        if (canInstall) {
                            context.startActivity(viewModel.installIntent(current.apkFile))
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                )

                is UpdateUiState.Error -> Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Ошибка",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(current.message)
                        Button(
                            onClick = {
                                current.update?.let(viewModel::download) ?: viewModel.check()
                            },
                        ) {
                            Text("Повторить")
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ProgressCard(text: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator()
            Text(text)
        }
    }
}

@Composable
private fun UpdateCard(
    title: String,
    tag: String,
    notes: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(tag, color = MaterialTheme.colorScheme.primary)
            if (notes.isNotBlank()) {
                Text(notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(actionText)
            }
        }
    }
}
