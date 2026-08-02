package com.norvexa.clearup.feature.privilege

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.privilege.PrivilegeManager
import com.norvexa.clearup.domain.model.PrivilegeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PrivilegeUiState(
    val loading: Boolean = true,
    val state: PrivilegeState = PrivilegeState(),
    val error: String? = null,
)

class PrivilegeViewModel(private val manager: PrivilegeManager) : ViewModel() {
    private val _ui = MutableStateFlow(PrivilegeUiState())
    val ui: StateFlow<PrivilegeUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = PrivilegeUiState(loading = true)
            runCatching {
                manager.readState(checkRoot = true)
            }.onSuccess { state ->
                _ui.value = PrivilegeUiState(loading = false, state = state)
            }.onFailure { error ->
                _ui.value = PrivilegeUiState(loading = false, error = error.message)
            }
        }
    }

    fun requestShizuku() {
        manager.requestShizukuPermission()
    }

    class Factory(private val manager: PrivilegeManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PrivilegeViewModel(manager) as T
    }
}
