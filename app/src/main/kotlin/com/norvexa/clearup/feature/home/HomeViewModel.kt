package com.norvexa.clearup.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.storage.AndroidStorageRepository
import com.norvexa.clearup.domain.model.StorageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val summary: StorageSummary = StorageSummary.Empty,
    val loading: Boolean = true,
    val error: String? = null,
)

class HomeViewModel(private val repository: AndroidStorageRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.loadSummary() }
                .onSuccess { summary -> _state.value = HomeUiState(summary = summary, loading = false) }
                .onFailure { error -> _state.value = HomeUiState(loading = false, error = error.message ?: "Не удалось прочитать накопитель") }
        }
    }

    class Factory(private val repository: AndroidStorageRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository) as T
    }
}
