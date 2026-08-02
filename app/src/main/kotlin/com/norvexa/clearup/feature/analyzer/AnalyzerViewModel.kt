package com.norvexa.clearup.feature.analyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.storage.AndroidStorageRepository
import com.norvexa.clearup.domain.model.StorageCategoryUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnalyzerUiState(
    val loading: Boolean = true,
    val categories: List<StorageCategoryUsage> = emptyList(),
    val error: String? = null,
)

class AnalyzerViewModel(private val repository: AndroidStorageRepository) : ViewModel() {
    private val _state = MutableStateFlow(AnalyzerUiState())
    val state: StateFlow<AnalyzerUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = AnalyzerUiState(loading = true)
            runCatching { repository.loadCategoryUsage() }
                .onSuccess { _state.value = AnalyzerUiState(loading = false, categories = it) }
                .onFailure { _state.value = AnalyzerUiState(loading = false, error = it.message) }
        }
    }

    class Factory(private val repository: AndroidStorageRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AnalyzerViewModel(repository) as T
    }
}
