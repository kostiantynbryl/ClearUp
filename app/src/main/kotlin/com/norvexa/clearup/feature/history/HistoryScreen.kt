package com.norvexa.clearup.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.util.ByteFormatter
import com.norvexa.clearup.core.util.DateFormatter
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.domain.model.HistoryType
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen(store: HistoryStore) {
    val entries by store.entries.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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
                TextButton(onClick = { scope.launch { store.clear() } }) {
                    Text("Очистить историю")
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
