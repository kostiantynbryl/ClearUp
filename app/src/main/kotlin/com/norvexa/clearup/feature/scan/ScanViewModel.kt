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
    val safeBytes: Long get() = items.filter { it.riskLevel == RiskLevel.SAFE }.sumOf { it.bytes }
    val reviewBytes: Long get() = items.filter { it.riskLevel != RiskLevel.SAFE }.sumOf { it.bytes }
    val reviewCount: Int get() = items.count { it.riskLevel != RiskLevel.SAFE }
}

class ScanViewModel(
    private val scannerEngine: ScannerEngine,
    private val trashManager: TrashManager,
    private val exclusions: ExclusionRepository,
    private val history: HistoryStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    private var lastThresholdMb = 512
    private var lastPreselectSafeItems = true

    fun scan(largeFileThresholdMb: Int, preselectSafeItems: Boolean) {
        if (_state.value.scanning) return
        lastThresholdMb = largeFileThresholdMb
        lastPreselectSafeItems = preselectSafeItems
        viewModelScope.launch {
            runScan(messageAfter = null, recordHistory = true)
        }
    }

    private suspend fun runScan(messageAfter: String?, recordHistory: Boolean) {
        _state.update { it.copy(scanning = true, error = null, message = null) }
        runCatching {
            scannerEngine.scan(
                lastThresholdMb,
                exclusions.current().pathPrefixes,
            )
        }.onSuccess { result ->
            _state.value = ScanUiState(
                scannedCount = result.scannedCount,
                items = result.items.map { item ->
                    item.copy(
                        selected = lastPreselectSafeItems && item.riskLevel == RiskLevel.SAFE,
                    )
                },
                message = messageAfter ?: if (result.items.isEmpty()) {
                    "Подходящие файлы не найдены"
                } else {
                    null
                },
            )
            if (recordHistory) {
                history.record(
                    type = HistoryType.SCAN,
                    itemCount = result.items.size,
                    bytes = result.reclaimableBytes,
                    note = "Обычное сканирование · только SAFE считается освобождаемым",
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(
                    scanning = false,
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
                items = state.items.map { it.copy(selected = it.riskLevel == RiskLevel.SAFE) },
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
        if (selected.isEmpty()) return
        viewModelScope.launch {
            history.record(
                type = HistoryType.CLEANUP,
                itemCount = selected.size,
                bytes = selected.sumOf { it.bytes },
                note = "Файлы подтверждены Android и перемещены в системную корзину",
            )
            runScan(
                messageAfter = "Готово: Android подтвердил перемещение ${selected.size} файлов в корзину",
                recordHistory = false,
            )
        }
    }

    fun onTrashCancelled() {
        _state.update {
            it.copy(message = "Очистка отменена — файлы не изменены")
        }
    }

    fun onDirectTrashResult(action: TrashAction.Completed) {
        val selected = _state.value.selectedItems
        viewModelScope.launch {
            if (action.movedCount > 0) {
                history.record(
                    type = HistoryType.CLEANUP,
                    itemCount = action.movedCount,
                    bytes = selected.sumOf { it.bytes },
                    note = "Прямое удаление на старой версии Android",
                )
            }
            if (action.movedCount > 0) {
                runScan(
                    messageAfter = "Удалено: ${action.movedCount}, ошибок: ${action.failedCount}",
                    recordHistory = false,
                )
            } else {
                _state.update {
                    it.copy(message = "Удалено: 0, ошибок: ${action.failedCount}")
                }
            }
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
