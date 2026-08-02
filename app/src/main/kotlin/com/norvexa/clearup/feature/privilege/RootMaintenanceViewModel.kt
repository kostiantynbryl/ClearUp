package com.norvexa.clearup.feature.privilege

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.exclusions.ExclusionRepository
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.data.privilege.RootAuditStore
import com.norvexa.clearup.data.privilege.RootOrphanRepository
import com.norvexa.clearup.domain.model.HistoryType
import com.norvexa.clearup.domain.model.OrphanDirectory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RootMaintenanceUiState(
    val loading: Boolean = false,
    val deleting: Boolean = false,
    val candidates: List<OrphanDirectory> = emptyList(),
    val error: String? = null,
    val message: String? = null,
) {
    val selected: List<OrphanDirectory> get() = candidates.filter { it.selected }
    val selectedBytes: Long get() = selected.sumOf { it.bytes }
    val totalBytes: Long get() = candidates.sumOf { it.bytes }
}

class RootMaintenanceViewModel(
    private val repository: RootOrphanRepository,
    private val exclusions: ExclusionRepository,
    private val history: HistoryStore,
    private val auditStore: RootAuditStore,
) : ViewModel() {
    private val _state = MutableStateFlow(RootMaintenanceUiState())
    val state: StateFlow<RootMaintenanceUiState> = _state.asStateFlow()
    val auditEntries = auditStore.entries

    fun scan() {
        if (_state.value.loading || _state.value.deleting) return
        viewModelScope.launch {
            _state.value = RootMaintenanceUiState(loading = true)
            runCatching {
                repository.scan(exclusions.current().packages)
            }.onSuccess { candidates ->
                _state.value = RootMaintenanceUiState(
                    candidates = candidates,
                    message = if (candidates.isEmpty()) {
                        "Остатки удалённых приложений не найдены"
                    } else {
                        null
                    },
                )
                history.record(
                    type = HistoryType.SCAN,
                    itemCount = candidates.size,
                    bytes = candidates.sumOf { it.bytes },
                    note = "Root-поиск остатков приложений",
                )
            }.onFailure { error ->
                _state.value = RootMaintenanceUiState(
                    error = error.message ?: "Не удалось проверить Root-каталоги",
                )
            }
        }
    }

    fun toggle(path: String) {
        _state.update { state ->
            state.copy(
                candidates = state.candidates.map { candidate ->
                    if (candidate.path == path) {
                        candidate.copy(selected = !candidate.selected)
                    } else {
                        candidate
                    }
                },
            )
        }
    }

    fun clearSelection() {
        _state.update { state ->
            state.copy(candidates = state.candidates.map { it.copy(selected = false) })
        }
    }

    fun deleteSelected() {
        val selected = _state.value.selected
        if (selected.isEmpty() || _state.value.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null, message = null) }
            val deletedPaths = linkedSetOf<String>()
            var failed = 0
            selected.forEach { candidate ->
                val result = runCatching { repository.delete(candidate) }.getOrNull()
                if (result?.success == true) {
                    deletedPaths += candidate.path
                } else {
                    failed++
                }
            }
            history.record(
                type = HistoryType.CLEANUP,
                itemCount = deletedPaths.size,
                bytes = selected.filter { it.path in deletedPaths }.sumOf { it.bytes },
                note = "Root-удаление остатков приложений",
            )
            _state.update { state ->
                state.copy(
                    deleting = false,
                    candidates = state.candidates.filterNot { it.path in deletedPaths },
                    message = "Удалено: ${deletedPaths.size}, ошибок: $failed",
                )
            }
        }
    }

    fun clearAudit() {
        viewModelScope.launch { auditStore.clear() }
    }

    class Factory(
        private val repository: RootOrphanRepository,
        private val exclusions: ExclusionRepository,
        private val history: HistoryStore,
        private val auditStore: RootAuditStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RootMaintenanceViewModel(
                repository = repository,
                exclusions = exclusions,
                history = history,
                auditStore = auditStore,
            ) as T
    }
}
