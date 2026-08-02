package com.norvexa.clearup.feature.history

import android.content.Intent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.util.ByteFormatter
import com.norvexa.clearup.core.util.DateFormatter
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.data.report.ReportExporter
import com.norvexa.clearup.domain.model.HistoryType
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(
    store: HistoryStore,
    exporter: ReportExporter,
) {
    val entries by store.entries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var exporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("История", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Локальный журнал сканирований и очисток. Хранятся последние 200 событий.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                exporting = true
                                exportError = null
                                runCatching {
                                    val shareIntent = exporter.createHistoryShareIntent(entries)
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            "Отправить отчёт ClearUp",
                                        ),
                                    )
                                }.onFailure { error ->
                                    exportError = error.message
                                        ?: "Не удалось экспортировать историю"
                                }
                                exporting = false
                            }
                        },
                        enabled = !exporting,
                    ) {
                        if (exporting) {
                            CircularProgressIndicator()
                        } else {
                            Text("Экспортировать JSON")
                        }
                    }
                    TextButton(
                        onClick = { scope.launch { store.clear() } },
                        enabled = !exporting,
                    ) {
                        Text("Очистить историю")
                    }
                }
            }
        }

        exportError?.let { error ->
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

        items(entries, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(historyTypeLabel(entry.type), style = MaterialTheme.typography.titleMedium)
                    Text(entry.note)
                    Text("${entry.itemCount} объектов · ${ByteFormatter.format(entry.bytes)}")
                    Text(
                        DateFormatter.format(entry.createdAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (entries.isEmpty()) {
            item { Text("История пока пуста.") }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

private fun historyTypeLabel(type: HistoryType): String = when (type) {
    HistoryType.SCAN -> "Сканирование"
    HistoryType.CLEANUP -> "Очистка"
    HistoryType.AUTOMATION -> "Автоматизация"
    HistoryType.APP_ACTION -> "Действие с приложением"
    HistoryType.UPDATE -> "Обновление"
}
