package com.norvexa.clearup.feature.exclusions

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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.data.exclusions.ExclusionRepository
import com.norvexa.clearup.data.exclusions.Exclusions
import kotlinx.coroutines.launch

@Composable
fun ExclusionsScreen(repository: ExclusionRepository) {
    val exclusions by repository.exclusions.collectAsStateWithLifecycle(
        initialValue = Exclusions(),
    )
    val scope = rememberCoroutineScope()
    var pathValue by remember { mutableStateOf("") }
    var packageValue by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Исключения", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Указанные пути не участвуют в сканировании. Пакеты защищены от автоматических и привилегированных действий.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Путь или папка", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = pathValue,
                        onValueChange = { pathValue = it },
                        label = { Text("Например: Download/Work/") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                repository.addPath(pathValue)
                                pathValue = ""
                            }
                        },
                        enabled = pathValue.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Добавить путь")
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Пакет приложения", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = packageValue,
                        onValueChange = { packageValue = it },
                        label = { Text("Например: org.example.app") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                repository.addPackage(packageValue)
                                packageValue = ""
                            }
                        },
                        enabled = packageValue.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Добавить пакет")
                    }
                }
            }
        }
        if (exclusions.pathPrefixes.isNotEmpty()) {
            item { Text("Исключённые пути", style = MaterialTheme.typography.titleLarge) }
            items(exclusions.pathPrefixes.sorted(), key = { "path:$it" }) { path ->
                ExclusionRow(
                    value = path,
                    onRemove = { scope.launch { repository.removePath(path) } },
                )
            }
        }
        if (exclusions.packages.isNotEmpty()) {
            item { Text("Защищённые пакеты", style = MaterialTheme.typography.titleLarge) }
            items(exclusions.packages.sorted(), key = { "package:$it" }) { packageName ->
                ExclusionRow(
                    value = packageName,
                    onRemove = { scope.launch { repository.removePackage(packageName) } },
                )
            }
        }
        if (exclusions.pathPrefixes.isEmpty() && exclusions.packages.isEmpty()) {
            item { Text("Исключений пока нет.") }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ExclusionRow(
    value: String,
    onRemove: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "Удалить исключение")
            }
        }
    }
}
