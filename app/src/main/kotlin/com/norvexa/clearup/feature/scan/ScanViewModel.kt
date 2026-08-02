package com.norvexa.clearup.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.cleanup.TrashAction
import com.norvexa.clearup.data.cleanup.TrashManager
import com.norvexa.clearup.data.exclusions.ExclusionRepository
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.data.scanner.ScannerEngine
import com.norvexa.clearup.domain.model.HistoryType
import com.norvexa.clearup.domain.model.RiskLevel
import com.norvexa.clearup.domain.model.ScanItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanUiState(
    val scanning: Boolean = false,
    val scannedCount: Int = 0,
    val items: List<ScanItem> = emptyList(),
    val error: String? = null,
    val message: String? = null,
) {
    val selectedItems: List<ScanItem> get() = items.filter { it.selected }
    val selectedBytes: Long get() = selectedItems.sumOf { it.bytes }
    val totalBytes: Long get() = items.sumOf { it.bytes }
}

class ScanViewModel(
    private val scannerEngine: ScannerEngine,
    private val trashManager: TrashManager,
    private val exclusions: ExclusionRepository,
    private val history: HistoryStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun scan(largeFileThresholdMb: Int, preselectSafeItems: Boolean) {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.value = ScanUiState(scanning = true)
            runCatching {
                scannerEngine.scan(
                    largeFileThresholdMb,
                    exclusions.current().pathPrefixes,
                )
            }.onSuccess { result ->
                _state.value = ScanUiState(
                    scannedCount = result.scannedCount,
                    items = result.items.map { item ->
                        item.copy(
                            selected = preselectSafeItems && item.riskLevel == RiskLevel.SAFE,
                        )
                    },
                    message = if (result.items.isEmpty()) {
                        "Подходящие файлы не найдены"
                    } else {
                        null
                    },
                )
                history.record(
                    type = HistoryType.SCAN,
                    itemCount = result.items.size,
                    bytes = result.reclaimableBytes,
                    note = "Обычное сканирование",
                )
            }.onFailure { error ->
                _state.value = ScanUiState(
                    error = error.message ?: "Ошибка сканирования",
                )
            }
        }
    }

    fun toggle(uri: String) {
        _state.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.uri == uri) item.copy(selected = !item.selected) else item
                },
            )
        }
    }

    fun selectSafe() {
        _state.update { state ->
            state.copy(
                items = state.items.map {
                    it.copy(selected = it.riskLevel == RiskLevel.SAFE)
                },
            )
        }
    }

    fun clearSelection() {
        _state.update { state ->
            state.copy(items = state.items.map { it.copy(selected = false) })
        }
    }

    suspend fun prepareTrash(): TrashAction = trashManager.prepare(_state.value.selectedItems)

    fun onTrashCompleted() {
        val selected = _state.value.selectedItems
        val uris = selected.mapTo(hashSetOf()) { it.uri }
        viewModelScope.launch {
            history.record(
                type = HistoryType.CLEANUP,
                itemCount = selected.size,
                bytes = selected.sumOf { it.bytes },
                note = "Файлы перемещены в корзину",
            )
        }
        _state.update { state ->
            state.copy(
                items = state.items.filterNot { item -> item.uri in uris },
                message = "Выбранные файлы перемещены в системную корзину",
            )
        }
    }

    fun onDirectTrashResult(action: TrashAction.Completed) {
        val selected = _state.value.selectedItems
        val uris = selected.mapTo(hashSetOf()) { it.uri }
        if (action.movedCount > 0) {
            viewModelScope.launch {
                history.record(
                    type = HistoryType.CLEANUP,
                    itemCount = action.movedCount,
                    bytes = selected.sumOf { it.bytes },
                    note = "Прямое удаление на старой версии Android",
                )
            }
        }
        _state.update { state ->
            state.copy(
                items = if (action.failedCount == 0) {
                    state.items.filterNot { item -> item.uri in uris }
                } else {
                    state.items
                },
                message = "Удалено: ${action.movedCount}, ошибок: ${action.failedCount}",
            )
        }
    }

    class Factory(
        private val scanner: ScannerEngine,
        private val trash: TrashManager,
        private val exclusions: ExclusionRepository,
        private val history: HistoryStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScanViewModel(
            scanner,
            trash,
            exclusions,
            history,
        ) as T
    }
}
