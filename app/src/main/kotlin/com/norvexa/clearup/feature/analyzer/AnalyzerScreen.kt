package com.norvexa.clearup.feature.analyzer

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.util.ByteFormatter

@Composable
fun AnalyzerScreen(viewModel: AnalyzerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when {
        state.loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        state.error != null -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) { Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error) }
        else -> {
            val max = state.categories.maxOfOrNull { it.bytes }?.coerceAtLeast(1) ?: 1
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text("Анализатор хранилища", style = MaterialTheme.typography.headlineMedium)
                    Text("Категории считаются локально по доступным данным MediaStore.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(state.categories, key = { it.category.name }) { usage ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(usage.category.displayName, style = MaterialTheme.typography.titleMedium)
                            Text("${ByteFormatter.format(usage.bytes)} · ${usage.count} файлов")
                            LinearProgressIndicator(
                                progress = { usage.bytes.toFloat() / max.toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (state.categories.isEmpty()) {
                    item { Text("Разрешите доступ к медиа, чтобы увидеть распределение файлов.") }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
