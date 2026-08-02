package com.norvexa.clearup.feature.duplicates

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.norvexa.clearup.core.util.ByteFormatter
import com.norvexa.clearup.data.cleanup.TrashAction
import kotlinx.coroutines.launch

@Composable
fun DuplicatesScreen(viewModel: DuplicatesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onTrashCompleted()
    }

    LaunchedEffect(Unit) {
        if (state.groups.isEmpty() && !state.loading) viewModel.scan()
    }

    Column(Modifier.fillMaxSize()) {
        when {
            state.loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Сравниваем содержимое файлов…")
            }

            state.error != null -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = viewModel::scan) { Text("Повторить") }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text("Точные дубликаты", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "SHA-256 сравнивается только у файлов одинакового размера. Первый, самый новый файл в группе сохраняется.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text("Кандидатов: ${state.candidateCount} · групп: ${state.groups.size}")
                        state.message?.let {
                            Text(it, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    items(state.groups, key = { it.fingerprint }) { group ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "${group.items.size} копии · ${ByteFormatter.format(group.reclaimableBytes)}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                group.items.forEachIndexed { index, item ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = item.selected,
                                            onCheckedChange = {
                                                if (index > 0) viewModel.toggle(item.uri)
                                            },
                                            enabled = index > 0,
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                if (index == 0) "Оригинал: ${item.displayName}" else item.displayName,
                                                maxLines = 1,
                                            )
                                            Text(
                                                item.path,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                        Text(
                                            ByteFormatter.format(item.bytes),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.groups.isEmpty()) {
                        item { Text("Точные дубликаты не найдены.") }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
                Button(
                    onClick = {
                        scope.launch {
                            when (val action = viewModel.prepareTrash()) {
                                is TrashAction.RequiresConfirmation -> launcher.launch(
                                    IntentSenderRequest.Builder(action.pendingIntent).build(),
                                )
                                is TrashAction.Completed -> viewModel.onDirectTrashResult(action)
                                TrashAction.NothingSelected -> Unit
                            }
                        }
                    },
                    enabled = state.selected.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text("В корзину · ${ByteFormatter.format(state.selectedBytes)}")
                }
            }
        }
    }
}
