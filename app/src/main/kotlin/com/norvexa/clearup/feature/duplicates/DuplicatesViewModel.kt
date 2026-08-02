package com.norvexa.clearup.feature.duplicates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.cleanup.TrashAction
import com.norvexa.clearup.data.cleanup.TrashManager
import com.norvexa.clearup.data.duplicates.DuplicateRepository
import com.norvexa.clearup.data.exclusions.ExclusionRepository
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.domain.model.CleanerCategory
import com.norvexa.clearup.domain.model.DuplicateGroup
import com.norvexa.clearup.domain.model.HistoryType
import com.norvexa.clearup.domain.model.RiskLevel
import com.norvexa.clearup.domain.model.ScanItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DuplicatesUiState(
    val loading: Boolean = false,
    val groups: List<DuplicateGroup> = emptyList(),
    val candidateCount: Int = 0,
    val error: String? = null,
    val message: String? = null,
) {
    val selected = groups.flatMap { it.items }.filter { it.selected }
    val selectedBytes = selected.sumOf { it.bytes }
}

class DuplicatesViewModel(
    private val repository: DuplicateRepository,
    private val exclusions: ExclusionRepository,
    private val trashManager: TrashManager,
    private val historyStore: HistoryStore,
) : ViewModel() {
    private val _state = MutableStateFlow(DuplicatesUiState())
    val state: StateFlow<DuplicatesUiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = DuplicatesUiState(loading = true)
            runCatching {
                repository.scanExact(exclusions.current().pathPrefixes)
            }.onSuccess { result ->
                _state.value = DuplicatesUiState(
                    groups = result.groups,
                    candidateCount = result.candidateCount,
                )
                historyStore.record(
                    type = HistoryType.SCAN,
                    itemCount = result.duplicateCount,
                    bytes = result.reclaimableBytes,
                    note = "Поиск точных дубликатов",
                )
            }.onFailure { error ->
                _state.value = DuplicatesUiState(
                    error = error.message ?: "Ошибка поиска дубликатов",
                )
            }
        }
    }

    fun toggle(uri: String) {
        _state.update { state ->
            state.copy(
                groups = state.groups.map { group ->
                    group.copy(
                        items = group.items.map { item ->
                            if (item.uri == uri) item.copy(selected = !item.selected) else item
                        },
                    )
                },
            )
        }
    }

    suspend fun prepareTrash(): TrashAction {
        val items = _state.value.selected.map { duplicate ->
            ScanItem(
                id = duplicate.id,
                displayName = duplicate.displayName,
                path = duplicate.path,
                uri = duplicate.uri,
                bytes = duplicate.bytes,
                category = CleanerCategory.OTHER,
                riskLevel = RiskLevel.REVIEW,
                reason = "Точный дубликат",
                modifiedAtMillis = duplicate.modifiedAtMillis,
                selected = true,
            )
        }
        return trashManager.prepare(items)
    }

    fun onDirectTrashResult(action: TrashAction.Completed) {
        if (action.failedCount == 0) {
            onTrashCompleted()
        } else {
            _state.update { state ->
                state.copy(
                    message = "Удалено: ${action.movedCount}, ошибок: ${action.failedCount}. Повторно просканируйте файлы.",
                )
            }
        }
    }

    fun onTrashCompleted() {
        val selected = _state.value.selected
        val selectedUris = selected.mapTo(hashSetOf()) { it.uri }
        viewModelScope.launch {
            historyStore.record(
                type = HistoryType.CLEANUP,
                itemCount = selected.size,
                bytes = selected.sumOf { it.bytes },
                note = "Дубликаты перемещены в корзину",
            )
        }
        _state.update { state ->
            state.copy(
                groups = state.groups
                    .map { group ->
                        group.copy(items = group.items.filterNot { it.uri in selectedUris })
                    }
                    .filter { it.items.size > 1 },
                message = "Дубликаты перемещены в системную корзину",
            )
        }
    }

    class Factory(
        private val repository: DuplicateRepository,
        private val exclusions: ExclusionRepository,
        private val trashManager: TrashManager,
        private val historyStore: HistoryStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DuplicatesViewModel(
            repository,
            exclusions,
            trashManager,
            historyStore,
        ) as T
    }
}
