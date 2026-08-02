package com.norvexa.clearup.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.norvexa.clearup.core.designsystem.InfoCard

@Composable
fun ToolsScreen(
    onAnalyzer: () -> Unit,
    onDuplicates: () -> Unit,
    onExclusions: () -> Unit,
    onHistory: () -> Unit,
    onPrivileges: () -> Unit,
    onAccessibility: () -> Unit,
    onRootMaintenance: () -> Unit,
    onUpdate: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Инструменты", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Анализ и контроль без рекламных модулей",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            InfoCard(
                title = "Анализатор хранилища",
                body = "Категории и размеры доступных файлов",
                icon = Icons.Outlined.Storage,
                modifier = Modifier.clickable(onClick = onAnalyzer),
            )
        }
        item {
            InfoCard(
                title = "Точные дубликаты",
                body = "Проверка размера и SHA-256",
                icon = Icons.Outlined.ContentCopy,
                modifier = Modifier.clickable(onClick = onDuplicates),
            )
        }
        item {
            InfoCard(
                title = "Исключения",
                body = "Защищённые пути и приложения",
                icon = Icons.Outlined.FolderOff,
                modifier = Modifier.clickable(onClick = onExclusions),
            )
        }
        item {
            InfoCard(
                title = "История",
                body = "Последние сканирования и очистки",
                icon = Icons.Outlined.History,
                modifier = Modifier.clickable(onClick = onHistory),
            )
        }
        item {
            InfoCard(
                title = "Accessibility-помощник",
                body = "Очистка кэша через системный экран без Root и Shizuku",
                icon = Icons.Outlined.AccessibilityNew,
                modifier = Modifier.clickable(onClick = onAccessibility),
            )
        }
        item {
            InfoCard(
                title = "Root-обслуживание",
                body = "Остатки удалённых приложений и локальный аудит Root-команд",
                icon = Icons.Outlined.FolderDelete,
                modifier = Modifier.clickable(onClick = onRootMaintenance),
            )
        }
        item {
            InfoCard(
                title = "Обновление",
                body = "GitHub Releases, SHA-256 и проверка подписи",
                icon = Icons.Outlined.SystemUpdate,
                modifier = Modifier.clickable(onClick = onUpdate),
            )
        }
        item {
            InfoCard(
                title = "Root и Shizuku",
                body = "Статус расширенных режимов",
                icon = Icons.Outlined.Security,
                modifier = Modifier.clickable(onClick = onPrivileges),
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
