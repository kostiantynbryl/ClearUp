package com.norvexa.clearup.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.cleanup.TrashAction
import com.norvexa.clearup.data.cleanup.TrashManager
import com.norvexa.clearup.data.scanner.ScannerEngine
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
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    fun scan(largeFileThresholdMb: Int) {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.value = ScanUiState(scanning = true)
            runCatching { scannerEngine.scan(largeFileThresholdMb) }
                .onSuccess { result ->
                    _state.value = ScanUiState(
                        scanning = false,
                        scannedCount = result.scannedCount,
                        items = result.items,
                        message = if (result.items.isEmpty()) "Подходящие файлы не найдены" else null,
                    )
                }
                .onFailure { error ->
                    _state.value = ScanUiState(scanning = false, error = error.message ?: "Ошибка сканирования")
                }
        }
    }

    fun toggle(uri: String) {
        _state.update { current ->
            current.copy(items = current.items.map { if (it.uri == uri) it.copy(selected = !it.selected) else it })
        }
    }

    fun selectSafe() {
        _state.update { current ->
            current.copy(items = current.items.map { it.copy(selected = it.riskLevel == RiskLevel.SAFE) })
        }
    }

    fun clearSelection() {
        _state.update { current -> current.copy(items = current.items.map { it.copy(selected = false) }) }
    }

    suspend fun prepareTrash(): TrashAction = trashManager.prepare(_state.value.selectedItems)

    fun onTrashCompleted() {
        val selectedUris = _state.value.selectedItems.mapTo(hashSetOf()) { it.uri }
        _state.update { current ->
            current.copy(
                items = current.items.filterNot { it.uri in selectedUris },
                message = "Выбранные файлы перемещены в системную корзину",
            )
        }
    }

    fun onDirectTrashResult(action: TrashAction.Completed) {
        val selectedUris = _state.value.selectedItems.mapTo(hashSetOf()) { it.uri }
        _state.update { current ->
            current.copy(
                items = if (action.failedCount == 0) current.items.filterNot { it.uri in selectedUris } else current.items,
                message = "Удалено: ${action.movedCount}, ошибок: ${action.failedCount}",
            )
        }
    }

    class Factory(
        private val scannerEngine: ScannerEngine,
        private val trashManager: TrashManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ScanViewModel(scannerEngine, trashManager) as T
    }
}
