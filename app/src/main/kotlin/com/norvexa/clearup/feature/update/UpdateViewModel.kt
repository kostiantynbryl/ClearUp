package com.norvexa.clearup.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.data.update.UpdateRepository
import com.norvexa.clearup.domain.model.HistoryType
import com.norvexa.clearup.domain.model.ReleaseUpdate
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val update: ReleaseUpdate) : UpdateUiState
    data class Downloading(val update: ReleaseUpdate) : UpdateUiState
    data class Ready(val update: ReleaseUpdate, val apkFile: File) : UpdateUiState
    data class Error(
        val message: String,
        val update: ReleaseUpdate? = null,
    ) : UpdateUiState
}

class UpdateViewModel(
    private val repository: UpdateRepository,
    private val history: HistoryStore,
) : ViewModel() {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun check() {
        if (
            _state.value is UpdateUiState.Checking ||
            _state.value is UpdateUiState.Downloading
        ) {
            return
        }
        viewModelScope.launch {
            _state.value = UpdateUiState.Checking
            try {
                val update = repository.checkLatest()
                _state.value = if (update == null) {
                    UpdateUiState.UpToDate
                } else {
                    UpdateUiState.Available(update)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = UpdateUiState.Error(
                    error.message ?: "Не удалось проверить обновления",
                )
            }
        }
    }

    fun download(update: ReleaseUpdate) {
        if (_state.value is UpdateUiState.Downloading) return
        viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(update)
            try {
                val file = repository.downloadVerified(update)
                history.record(
                    type = HistoryType.UPDATE,
                    itemCount = 1,
                    bytes = file.length(),
                    note = "Проверено обновление ${update.tag}",
                )
                _state.value = UpdateUiState.Ready(update, file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _state.value = UpdateUiState.Error(
                    message = error.message ?: "Не удалось загрузить обновление",
                    update = update,
                )
            }
        }
    }

    fun installIntent(file: File) = repository.createInstallIntent(file)

    class Factory(
        private val repository: UpdateRepository,
        private val history: HistoryStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UpdateViewModel(
            repository = repository,
            history = history,
        ) as T
    }
}
