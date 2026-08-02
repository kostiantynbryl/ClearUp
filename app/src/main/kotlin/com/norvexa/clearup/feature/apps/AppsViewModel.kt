package com.norvexa.clearup.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.apps.AndroidAppRepository
import com.norvexa.clearup.domain.model.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val allApps: List<InstalledApp> = emptyList(),
    val error: String? = null,
) {
    val visibleApps: List<InstalledApp>
        get() = if (query.isBlank()) allApps else allApps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
}

class AppsViewModel(private val repository: AndroidAppRepository) : ViewModel() {
    private val _state = MutableStateFlow(AppsUiState())
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    fun load(includeSystemApps: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { repository.loadApps(includeSystemApps) }
                .onSuccess { apps -> _state.update { it.copy(loading = false, allApps = apps) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.message) } }
        }
    }

    fun setQuery(query: String) { _state.update { it.copy(query = query) } }

    class Factory(private val repository: AndroidAppRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppsViewModel(repository) as T
    }
}
