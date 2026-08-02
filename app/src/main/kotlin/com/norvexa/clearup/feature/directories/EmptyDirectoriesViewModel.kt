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
                    message = null,
                    error = null,
                )
            }
            runCatching {
                repository.scan(ageDays)
            }.onSuccess { directories ->
                history.record(
                    type = HistoryType.SCAN,
                    itemCount = directories.size,
                    bytes = 0,
                    note = "EMPTY_DIRECTORIES: older_than_${ageDays}_days",
                )
                _state.update {
                    it.copy(
                        scanning = false,
                        directories = directories,
                        selectedPaths = emptySet(),
                        message = if (directories.isEmpty()) {
                            "Подходящих пустых каталогов не найдено"
                        } else {
                            "Найдено каталогов: ${directories.size}. Ничего не выбрано автоматически."
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
                val result = repository.deleteSelected(selected)
                if (result.deleted > 0) {
                    history.record(
                        type = HistoryType.CLEANUP,
                        itemCount = result.deleted,
                        bytes = 0,
                        note = "EMPTY_DIRECTORIES",
                    )
                }
                result to repository.scan(ageDays)
            }.onSuccess { (result, refreshedDirectories) ->
                _state.update {
                    it.copy(
                        deleting = false,
                        directories = refreshedDirectories,
                        selectedPaths = emptySet(),
                        message = buildString {
                            append("Удалено: ${result.deleted}")
                            if (result.skipped > 0) append(" · пропущено: ${result.skipped}")
                            if (result.failedPaths.isNotEmpty()) {
                                append(" · ошибок: ${result.failedPaths.size}")
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
