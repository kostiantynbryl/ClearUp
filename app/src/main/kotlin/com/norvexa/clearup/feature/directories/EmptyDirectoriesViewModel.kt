package com.norvexa.clearup.feature.directories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.directories.EmptyDirectoryRepository
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.domain.model.EmptyDirectory
import com.norvexa.clearup.domain.model.HistoryType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmptyDirectoriesUiState(
    val accessAvailable: Boolean = false,
    val scanning: Boolean = false,
    val deleting: Boolean = false,
    val minAgeDays: Int = EmptyDirectoryRepository.DEFAULT_MIN_AGE_DAYS,
    val directories: List<EmptyDirectory> = emptyList(),
    val selectedPaths: Set<String> = emptySet(),
    val visitedDirectories: Int = 0,
    val scanLimitReached: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val selected: List<EmptyDirectory>
        get() = directories.filter { it.canonicalPath in selectedPaths }
}

class EmptyDirectoriesViewModel(
    private val repository: EmptyDirectoryRepository,
    private val history: HistoryStore,
) : ViewModel() {
    private val _state = MutableStateFlow(
        EmptyDirectoriesUiState(accessAvailable = repository.hasRequiredAccess()),
    )
    val state: StateFlow<EmptyDirectoriesUiState> = _state.asStateFlow()

    fun refreshAccess() {
        _state.update { it.copy(accessAvailable = repository.hasRequiredAccess()) }
    }

    fun setMinAgeDays(days: Int) {
        if (days !in ALLOWED_MIN_AGE_DAYS || _state.value.scanning || _state.value.deleting) return
        _state.update {
            it.copy(
                minAgeDays = days,
                directories = emptyList(),
                selectedPaths = emptySet(),
                visitedDirectories = 0,
                scanLimitReached = false,
                message = null,
                error = null,
            )
        }
    }

    fun scan() {
        if (_state.value.scanning || _state.value.deleting) return
        viewModelScope.launch {
            val accessAvailable = repository.hasRequiredAccess()
            if (!accessAvailable) {
                _state.update {
                    it.copy(
                        accessAvailable = false,
                        error = "Предоставьте доступ к файлам и повторите сканирование",
                    )
                }
                return@launch
            }

            val ageDays = _state.value.minAgeDays
            _state.update {
                it.copy(
                    accessAvailable = true,
                    scanning = true,
                    selectedPaths = emptySet(),
                    visitedDirectories = 0,
                    scanLimitReached = false,
                    message = null,
                    error = null,
                )
            }
            runCatching {
                repository.scan(ageDays)
            }.onSuccess { scanResult ->
                history.record(
                    type = HistoryType.SCAN,
                    itemCount = scanResult.directories.size,
                    bytes = 0,
                    note = buildString {
                        append("EMPTY_DIRECTORIES: older_than_${ageDays}_days")
                        append("; visited=${scanResult.visitedDirectories}")
                        if (scanResult.limitReached) append("; partial=true")
                    },
                )
                _state.update {
                    it.copy(
                        scanning = false,
                        directories = scanResult.directories,
                        selectedPaths = emptySet(),
                        visitedDirectories = scanResult.visitedDirectories,
                        scanLimitReached = scanResult.limitReached,
                        message = when {
                            scanResult.limitReached ->
                                "Проверено ${scanResult.visitedDirectories} каталогов. Достигнут безопасный лимит, результаты частичные."
                            scanResult.directories.isEmpty() ->
                                "Проверено ${scanResult.visitedDirectories}. Подходящих пустых каталогов не найдено."
                            else ->
                                "Проверено ${scanResult.visitedDirectories}, найдено ${scanResult.directories.size}. Ничего не выбрано автоматически."
                        },
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        scanning = false,
                        accessAvailable = repository.hasRequiredAccess(),
                        error = error.message ?: "Не удалось проверить каталоги",
                    )
                }
            }
        }
    }

    fun toggle(path: String) {
        if (_state.value.scanning || _state.value.deleting) return
        _state.update { current ->
            val selected = current.selectedPaths.toMutableSet()
            if (!selected.add(path)) selected.remove(path)
            current.copy(selectedPaths = selected)
        }
    }

    fun selectAll() {
        if (_state.value.scanning || _state.value.deleting) return
        _state.update { current ->
            current.copy(
                selectedPaths = if (current.selectedPaths.size == current.directories.size) {
                    emptySet()
                } else {
                    current.directories.mapTo(linkedSetOf(), EmptyDirectory::canonicalPath)
                },
            )
        }
    }

    fun deleteSelected() {
        val selected = _state.value.selected
        if (selected.isEmpty() || _state.value.deleting || _state.value.scanning) return

        viewModelScope.launch {
            val ageDays = _state.value.minAgeDays
            _state.update { it.copy(deleting = true, message = null, error = null) }
            runCatching {
                val deleteResult = repository.deleteSelected(selected)
                if (deleteResult.deleted > 0) {
                    history.record(
                        type = HistoryType.CLEANUP,
                        itemCount = deleteResult.deleted,
                        bytes = 0,
                        note = "EMPTY_DIRECTORIES",
                    )
                }
                deleteResult to repository.scan(ageDays)
            }.onSuccess { (deleteResult, scanResult) ->
                _state.update {
                    it.copy(
                        deleting = false,
                        directories = scanResult.directories,
                        selectedPaths = emptySet(),
                        visitedDirectories = scanResult.visitedDirectories,
                        scanLimitReached = scanResult.limitReached,
                        message = buildString {
                            append("Удалено: ${deleteResult.deleted}")
                            if (deleteResult.skipped > 0) {
                                append(" · пропущено: ${deleteResult.skipped}")
                            }
                            if (deleteResult.failedPaths.isNotEmpty()) {
                                append(" · ошибок: ${deleteResult.failedPaths.size}")
                            }
                            if (scanResult.limitReached) {
                                append(" · повторное сканирование частичное")
                            }
                        },
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        deleting = false,
                        accessAvailable = repository.hasRequiredAccess(),
                        error = error.message ?: "Не удалось удалить выбранные каталоги",
                    )
                }
            }
        }
    }

    class Factory(
        private val repository: EmptyDirectoryRepository,
        private val history: HistoryStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            EmptyDirectoriesViewModel(repository, history) as T
    }

    companion object {
        val ALLOWED_MIN_AGE_DAYS = setOf(7, 14, 30, 90)
    }
}
