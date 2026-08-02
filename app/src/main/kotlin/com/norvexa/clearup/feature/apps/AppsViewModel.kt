package com.norvexa.clearup.feature.apps

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.accessibility.AccessibilityCacheCoordinator
import com.norvexa.clearup.data.accessibility.AccessibilityCacheSession
import com.norvexa.clearup.data.accessibility.AccessibilityCacheStage
import com.norvexa.clearup.data.apps.AndroidAppRepository
import com.norvexa.clearup.data.exclusions.ExclusionRepository
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.data.privilege.PrivilegeManager
import com.norvexa.clearup.data.privilege.RootShell
import com.norvexa.clearup.data.privilege.ShizukuCommandClient
import com.norvexa.clearup.domain.model.HistoryType
import com.norvexa.clearup.domain.model.InstalledApp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppActionBackend(val label: String) {
    NONE("Стандартный режим"),
    ACCESSIBILITY("Accessibility"),
    SHIZUKU("Shizuku"),
    ROOT("Root"),
}

data class AppsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val allApps: List<InstalledApp> = emptyList(),
    val includeSystemApps: Boolean = false,
    val ownPackageName: String = "",
    val rootAvailable: Boolean = false,
    val shizukuAvailable: Boolean = false,
    val accessibilityServiceEnabled: Boolean = false,
    val accessibilityConsentAccepted: Boolean = false,
    val accessibilityLaunchPackage: String? = null,
    val shizukuCacheClearSupported: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
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

    val actionBackend: AppActionBackend
        get() = when {
            rootAvailable -> AppActionBackend.ROOT
            shizukuAvailable -> AppActionBackend.SHIZUKU
            accessibilityServiceEnabled && accessibilityConsentAccepted ->
                AppActionBackend.ACCESSIBILITY
            else -> AppActionBackend.NONE
        }
}

enum class AppMaintenanceAction {
    CLEAR_CACHE,
    FORCE_STOP,
    FREEZE,
    UNFREEZE,
}

class AppsViewModel(
    private val repository: AndroidAppRepository,
    private val privilegeManager: PrivilegeManager,
    private val rootShell: RootShell,
    private val shizukuClient: ShizukuCommandClient,
    private val accessibilityCoordinator: AccessibilityCacheCoordinator,
    private val exclusions: ExclusionRepository,
    private val history: HistoryStore,
    private val ownPackageName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(AppsUiState(ownPackageName = ownPackageName))
    val state: StateFlow<AppsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            accessibilityCoordinator.state.collect { accessibility ->
                val session = accessibility.session
                _state.update { current ->
                    current.copy(
                        accessibilityServiceEnabled = accessibility.serviceEnabled,
                        accessibilityConsentAccepted = accessibility.consentAccepted,
                        busyPackage = when {
                            session?.active == true -> session.packageName
                            session != null && current.busyPackage == session.packageName -> null
                            else -> current.busyPackage
                        },
                    )
                }
                handleAccessibilityResult(session)
            }
        }
    }

    fun load(includeSystemApps: Boolean) {
        accessibilityCoordinator.refreshCapabilities()
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
                        shizukuAvailable = privilege.shizukuRunning &&
                            privilege.shizukuPermissionGranted &&
                            shizukuClient.isReady(),
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

    fun refreshAccessibilityState() {
        accessibilityCoordinator.refreshCapabilities()
    }

    fun consumeAccessibilityLaunch() {
        _state.update { it.copy(accessibilityLaunchPackage = null) }
    }

    fun accessibilityLaunchFailed(message: String) {
        accessibilityCoordinator.cancel(message)
        _state.update {
            it.copy(
                accessibilityLaunchPackage = null,
                busyPackage = null,
                error = message,
            )
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

    fun executeAction(app: InstalledApp, action: AppMaintenanceAction) {
        val current = _state.value
        val backend = current.actionBackend
        if (
            backend == AppActionBackend.NONE ||
            app.isSystem ||
            app.packageName in current.protectedPackages ||
            current.busyPackage != null ||
            (
                backend == AppActionBackend.SHIZUKU &&
                    action == AppMaintenanceAction.CLEAR_CACHE &&
                    !current.shizukuCacheClearSupported
                ) ||
            (
                backend == AppActionBackend.ACCESSIBILITY &&
                    action != AppMaintenanceAction.CLEAR_CACHE
                )
        ) {
            return
        }

        if (backend == AppActionBackend.ACCESSIBILITY) {
            runCatching {
                accessibilityCoordinator.begin(
                    packageName = app.packageName,
                    appLabel = app.label,
                    estimatedBytes = app.cacheBytes ?: 0,
                )
            }.onSuccess {
                _state.update {
                    it.copy(
                        busyPackage = app.packageName,
                        accessibilityLaunchPackage = app.packageName,
                        message = "Открываю системную карточку. Не закрывайте её до завершения запроса.",
                        error = null,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(error = error.message ?: "Не удалось запустить Accessibility-помощник")
                }
            }
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
            val result = when (backend) {
                AppActionBackend.ROOT -> when (action) {
                    AppMaintenanceAction.CLEAR_CACHE -> rootShell.clearCache(app.packageName)
                    AppMaintenanceAction.FORCE_STOP -> rootShell.forceStop(app.packageName)
                    AppMaintenanceAction.FREEZE -> rootShell.setFrozen(app.packageName, frozen = true)
                    AppMaintenanceAction.UNFREEZE -> rootShell.setFrozen(app.packageName, frozen = false)
                }
                AppActionBackend.SHIZUKU -> when (action) {
                    AppMaintenanceAction.CLEAR_CACHE -> shizukuClient.clearCache(app.packageName)
                    AppMaintenanceAction.FORCE_STOP -> shizukuClient.forceStop(app.packageName)
                    AppMaintenanceAction.FREEZE -> shizukuClient.setFrozen(app.packageName, frozen = true)
                    AppMaintenanceAction.UNFREEZE -> shizukuClient.setFrozen(app.packageName, frozen = false)
                }
                AppActionBackend.ACCESSIBILITY,
                AppActionBackend.NONE,
                -> return@launch
            }
            if (result.success) {
                history.record(
                    type = HistoryType.APP_ACTION,
                    itemCount = 1,
                    bytes = if (action == AppMaintenanceAction.CLEAR_CACHE) {
                        app.cacheBytes ?: 0
                    } else {
                        0
                    },
                    note = "${backend.name}:${action.name}: ${app.packageName}",
                )
                val refreshedApps = repository.loadApps(current.includeSystemApps)
                _state.update {
                    it.copy(
                        busyPackage = null,
                        allApps = refreshedApps,
                        message = actionSuccessMessage(action, backend),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        busyPackage = null,
                        error = result.error.ifBlank {
                            result.output.ifBlank {
                                "Операция ${backend.label} завершилась с ошибкой"
                            }
                        },
                    )
                }
            }
        }
    }

    private suspend fun handleAccessibilityResult(session: AccessibilityCacheSession?) {
        if (session == null || session.active || session.historyRecorded) return

        try {
            when (session.stage) {
                AccessibilityCacheStage.COMPLETED -> {
                    history.record(
                        type = HistoryType.APP_ACTION,
                        itemCount = 1,
                        bytes = session.estimatedBytes,
                        note = "ACCESSIBILITY:CLEAR_CACHE_CLICKED: ${session.packageName}",
                    )
                    val current = _state.value
                    val refreshedApps = if (current.allApps.isEmpty()) {
                        current.allApps
                    } else {
                        repository.loadApps(current.includeSystemApps)
                    }
                    _state.update {
                        it.copy(
                            busyPackage = null,
                            allApps = refreshedApps,
                            message = "Системная команда очистки кэша выполнена · Accessibility",
                            error = null,
                        )
                    }
                }
                AccessibilityCacheStage.FAILED -> {
                    _state.update {
                        it.copy(
                            busyPackage = null,
                            error = session.message,
                        )
                    }
                }
                AccessibilityCacheStage.CANCELLED -> {
                    _state.update {
                        it.copy(
                            busyPackage = null,
                            message = session.message,
                        )
                    }
                }
                AccessibilityCacheStage.WAITING_APP_DETAILS,
                AccessibilityCacheStage.WAITING_STORAGE_PAGE,
                -> Unit
            }
        } catch (error: Throwable) {
            _state.update {
                it.copy(
                    busyPackage = null,
                    error = error.message ?: "Не удалось обработать результат Accessibility",
                )
            }
        } finally {
            accessibilityCoordinator.markHistoryRecorded(session.id)
        }
    }

    private fun actionSuccessMessage(
        action: AppMaintenanceAction,
        backend: AppActionBackend,
    ): String {
        val message = when (action) {
            AppMaintenanceAction.CLEAR_CACHE -> "Кэш приложения очищен"
            AppMaintenanceAction.FORCE_STOP -> "Приложение остановлено"
            AppMaintenanceAction.FREEZE -> "Приложение заморожено"
            AppMaintenanceAction.UNFREEZE -> "Приложение разморожено"
        }
        return "$message · ${backend.label}"
    }

    class Factory(
        private val repository: AndroidAppRepository,
        private val privilegeManager: PrivilegeManager,
        private val rootShell: RootShell,
        private val shizukuClient: ShizukuCommandClient,
        private val accessibilityCoordinator: AccessibilityCacheCoordinator,
        private val exclusions: ExclusionRepository,
        private val history: HistoryStore,
        private val ownPackageName: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppsViewModel(
            repository = repository,
            privilegeManager = privilegeManager,
            rootShell = rootShell,
            shizukuClient = shizukuClient,
            accessibilityCoordinator = accessibilityCoordinator,
            exclusions = exclusions,
            history = history,
            ownPackageName = ownPackageName,
        ) as T
    }
}
