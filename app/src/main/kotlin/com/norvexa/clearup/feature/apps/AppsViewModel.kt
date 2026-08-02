package com.norvexa.clearup.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.apps.AndroidAppRepository
import com.norvexa.clearup.data.exclusions.ExclusionRepository
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.data.privilege.PrivilegeManager
import com.norvexa.clearup.data.privilege.RootShell
import com.norvexa.clearup.domain.model.HistoryType
import com.norvexa.clearup.domain.model.InstalledApp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val allApps: List<InstalledApp> = emptyList(),
    val includeSystemApps: Boolean = false,
    val ownPackageName: String = "",
    val rootAvailable: Boolean = false,
    val protectedPackages: Set<String> = emptySet(),
    val busyPackage: String? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val visibleApps: List<InstalledApp>
        get() = if (query.isBlank()) {
            allApps
        } else {
            allApps.filter { app ->
                app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            }
        }
}

enum class RootAppAction {
    CLEAR_CACHE,
    FORCE_STOP,
    FREEZE,
    UNFREEZE,
}

class AppsViewModel(
    private val repository: AndroidAppRepository,
    private val privilegeManager: PrivilegeManager,
    private val rootShell: RootShell,
    private val exclusions: ExclusionRepository,
    private val history: HistoryStore,
    private val ownPackageName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(AppsUiState(ownPackageName = ownPackageName))
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    fun load(includeSystemApps: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    includeSystemApps = includeSystemApps,
                )
            }
            runCatching {
                coroutineScope {
                    val apps = async { repository.loadApps(includeSystemApps) }
                    val privilege = async { privilegeManager.readState() }
                    val protected = async { exclusions.current().packages }
                    Triple(apps.await(), privilege.await(), protected.await())
                }
            }.onSuccess { (apps, privilege, protected) ->
                _state.update {
                    it.copy(
                        loading = false,
                        allApps = apps,
                        rootAvailable = privilege.rootAvailable,
                        protectedPackages = protected + ownPackageName,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = error.message ?: "Не удалось загрузить приложения",
                    )
                }
            }
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun setProtected(packageName: String, protected: Boolean) {
        if (packageName == ownPackageName) return
        viewModelScope.launch {
            if (protected) {
                exclusions.addPackage(packageName)
            } else {
                exclusions.removePackage(packageName)
            }
            val packages = exclusions.current().packages + ownPackageName
            _state.update {
                it.copy(
                    protectedPackages = packages,
                    message = if (protected) {
                        "Приложение добавлено в исключения"
                    } else {
                        "Приложение удалено из исключений"
                    },
                )
            }
        }
    }

    fun executeRootAction(app: InstalledApp, action: RootAppAction) {
        val current = _state.value
        if (
            !current.rootAvailable ||
            app.isSystem ||
            app.packageName in current.protectedPackages ||
            current.busyPackage != null
        ) {
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    busyPackage = app.packageName,
                    message = null,
                    error = null,
                )
            }
            val result = when (action) {
                RootAppAction.CLEAR_CACHE -> rootShell.clearCache(app.packageName)
                RootAppAction.FORCE_STOP -> rootShell.forceStop(app.packageName)
                RootAppAction.FREEZE -> rootShell.setFrozen(app.packageName, frozen = true)
                RootAppAction.UNFREEZE -> rootShell.setFrozen(app.packageName, frozen = false)
            }
            if (result.success) {
                history.record(
                    type = HistoryType.APP_ACTION,
                    itemCount = 1,
                    bytes = if (action == RootAppAction.CLEAR_CACHE) app.cacheBytes ?: 0 else 0,
                    note = "${action.name}: ${app.packageName}",
                )
                val refreshedApps = repository.loadApps(current.includeSystemApps)
                _state.update {
                    it.copy(
                        busyPackage = null,
                        allApps = refreshedApps,
                        message = rootActionSuccessMessage(action),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        busyPackage = null,
                        error = result.error.ifBlank {
                            result.output.ifBlank { "Root-команда завершилась с ошибкой" }
                        },
                    )
                }
            }
        }
    }

    private fun rootActionSuccessMessage(action: RootAppAction): String = when (action) {
        RootAppAction.CLEAR_CACHE -> "Кэш приложения очищен"
        RootAppAction.FORCE_STOP -> "Приложение остановлено"
        RootAppAction.FREEZE -> "Приложение заморожено"
        RootAppAction.UNFREEZE -> "Приложение разморожено"
    }

    class Factory(
        private val repository: AndroidAppRepository,
        private val privilegeManager: PrivilegeManager,
        private val rootShell: RootShell,
        private val exclusions: ExclusionRepository,
        private val history: HistoryStore,
        private val ownPackageName: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppsViewModel(
            repository = repository,
            privilegeManager = privilegeManager,
            rootShell = rootShell,
            exclusions = exclusions,
            history = history,
            ownPackageName = ownPackageName,
        ) as T
    }
}
