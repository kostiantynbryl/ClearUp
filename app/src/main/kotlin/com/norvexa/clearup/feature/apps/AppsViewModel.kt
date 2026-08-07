package com.norvexa.clearup.feature.apps

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.norvexa.clearup.data.accessibility.AccessibilityBatchStage
import com.norvexa.clearup.data.accessibility.AccessibilityCacheBatch
import com.norvexa.clearup.data.accessibility.AccessibilityCacheBatchItem
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
    ACCESSIBILITY("Спецвозможности"),
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
    val selectedPackages: Set<String> = emptySet(),
    val busyPackage: String? = null,
    val batchRunning: Boolean = false,
    val batchCompleted: Int = 0,
    val batchTotal: Int = 0,
    val batchFailed: Int = 0,
    val batchMessage: String? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val visibleApps: List<InstalledApp>
        get() {
            val filtered = if (query.isBlank()) {
                allApps
            } else {
                allApps.filter { app ->
                    app.label.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
                }
            }
            return filtered.sortedWith(
                compareByDescending<InstalledApp> { it.cacheBytes ?: -1L }
                    .thenBy { it.label.lowercase() },
            )
        }

    val actionBackend: AppActionBackend
        get() = when {
            rootAvailable -> AppActionBackend.ROOT
            shizukuAvailable -> AppActionBackend.SHIZUKU
            accessibilityServiceEnabled && accessibilityConsentAccepted ->
                AppActionBackend.ACCESSIBILITY
            else -> AppActionBackend.NONE
        }

    fun isCacheEligible(app: InstalledApp): Boolean =
        !app.isSystem &&
            app.packageName != ownPackageName &&
            app.packageName !in protectedPackages

    val selectedApps: List<InstalledApp>
        get() = allApps.filter { it.packageName in selectedPackages && isCacheEligible(it) }

    val selectedCacheBytes: Long
        get() = selectedApps.sumOf { it.cacheBytes ?: 0L }
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
                val batch = accessibility.batch
                _state.update { current ->
                    current.copy(
                        accessibilityServiceEnabled = accessibility.serviceEnabled,
                        accessibilityConsentAccepted = accessibility.consentAccepted,
                        busyPackage = when {
                            batch?.active == true -> batch.currentItem?.packageName
                            session?.active == true -> session.packageName
                            current.actionBackend == AppActionBackend.ACCESSIBILITY -> null
                            else -> current.busyPackage
                        },
                        batchRunning = batch?.active == true,
                        batchCompleted = batch?.completedCount ?: current.batchCompleted,
                        batchTotal = batch?.totalCount ?: current.batchTotal,
                        batchFailed = batch?.failedCount ?: current.batchFailed,
                        batchMessage = batch?.message,
                    )
                }
                if (batch != null) {
                    handleAccessibilityBatchResult(batch)
                } else {
                    handleLegacyAccessibilityResult(session)
                }
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
                _state.update { current ->
                    val protectedPackages = protected + ownPackageName
                    current.copy(
                        loading = false,
                        allApps = apps,
                        rootAvailable = privilege.rootAvailable,
                        shizukuAvailable = privilege.shizukuRunning &&
                            privilege.shizukuPermissionGranted &&
                            shizukuClient.isReady(),
                        protectedPackages = protectedPackages,
                        selectedPackages = current.selectedPackages.filterTo(hashSetOf()) { packageName ->
                            packageName !in protectedPackages && packageName != ownPackageName
                        },
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
                batchRunning = false,
                error = message,
            )
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun toggleSelected(packageName: String) {
        _state.update { current ->
            val app = current.allApps.firstOrNull { it.packageName == packageName }
                ?: return@update current
            if (!current.isCacheEligible(app) || current.batchRunning) return@update current
            val selected = current.selectedPackages.toMutableSet()
            if (!selected.add(packageName)) selected.remove(packageName)
            current.copy(selectedPackages = selected)
        }
    }

    fun selectVisibleEligible() {
        _state.update { current ->
            if (current.batchRunning) return@update current
            val packages = current.visibleApps
                .asSequence()
                .filter(current::isCacheEligible)
                .map { it.packageName }
                .take(MAX_BATCH_ITEMS)
                .toSet()
            current.copy(selectedPackages = packages)
        }
    }

    fun clearSelection() {
        _state.update { current ->
            if (current.batchRunning) current else current.copy(selectedPackages = emptySet())
        }
    }

    fun clearSelectedCaches() {
        val current = _state.value
        val selected = current.selectedApps.take(MAX_BATCH_ITEMS)
        if (selected.isEmpty() || current.batchRunning || current.busyPackage != null) return

        when (current.actionBackend) {
            AppActionBackend.NONE -> {
                _state.update {
                    it.copy(error = "Для скрытого кэша настройте Root, Shizuku или Спецвозможности")
                }
            }
            AppActionBackend.SHIZUKU -> {
                if (!current.shizukuCacheClearSupported) {
                    _state.update {
                        it.copy(error = "Shizuku cache-only поддерживается ClearUp на Android 13+")
                    }
                } else {
                    runPrivilegedBatch(selected, AppActionBackend.SHIZUKU)
                }
            }
            AppActionBackend.ROOT -> runPrivilegedBatch(selected, AppActionBackend.ROOT)
            AppActionBackend.ACCESSIBILITY -> startAccessibilityBatch(selected)
        }
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
                    selectedPackages = it.selectedPackages - packages,
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
            current.batchRunning ||
            (backend == AppActionBackend.SHIZUKU && action == AppMaintenanceAction.CLEAR_CACHE && !current.shizukuCacheClearSupported) ||
            (backend == AppActionBackend.ACCESSIBILITY && action != AppMaintenanceAction.CLEAR_CACHE)
        ) {
            return
        }

        if (backend == AppActionBackend.ACCESSIBILITY) {
            startAccessibilityBatch(listOf(app))
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(busyPackage = app.packageName, message = null, error = null) }
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
                    bytes = if (action == AppMaintenanceAction.CLEAR_CACHE) app.cacheBytes ?: 0 else 0,
                    note = "${backend.name}:${action.name}: ${app.packageName}",
                )
                _state.update {
                    it.copy(
                        busyPackage = null,
                        allApps = repository.loadApps(current.includeSystemApps),
                        message = actionSuccessMessage(action, backend),
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        busyPackage = null,
                        error = result.error.ifBlank { result.output.ifBlank { "Операция ${backend.label} завершилась с ошибкой" } },
                    )
                }
            }
        }
    }

    private fun runPrivilegedBatch(
        apps: List<InstalledApp>,
        backend: AppActionBackend,
    ) {
        viewModelScope.launch {
            var completed = 0
            var failed = 0
            var clearedBytes = 0L
            _state.update {
                it.copy(
                    batchRunning = true,
                    batchCompleted = 0,
                    batchTotal = apps.size,
                    batchFailed = 0,
                    batchMessage = "Начинаем очистку кэша",
                    message = null,
                    error = null,
                )
            }
            apps.forEachIndexed { index, app ->
                _state.update {
                    it.copy(
                        busyPackage = app.packageName,
                        batchMessage = "${app.label} · ${index + 1} из ${apps.size}",
                    )
                }
                val result = when (backend) {
                    AppActionBackend.ROOT -> rootShell.clearCache(app.packageName)
                    AppActionBackend.SHIZUKU -> shizukuClient.clearCache(app.packageName)
                    else -> return@forEachIndexed
                }
                if (result.success) {
                    completed += 1
                    clearedBytes += app.cacheBytes ?: 0L
                } else {
                    failed += 1
                }
                _state.update {
                    it.copy(batchCompleted = completed, batchFailed = failed)
                }
            }
            if (completed > 0) {
                history.record(
                    type = HistoryType.APP_ACTION,
                    itemCount = completed,
                    bytes = clearedBytes,
                    note = "${backend.name}:BATCH_CLEAR_CACHE",
                )
            }
            val refreshed = repository.loadApps(_state.value.includeSystemApps)
            _state.update {
                it.copy(
                    allApps = refreshed,
                    selectedPackages = emptySet(),
                    busyPackage = null,
                    batchRunning = false,
                    batchCompleted = completed,
                    batchFailed = failed,
                    batchMessage = null,
                    message = "Кэш очищен у $completed приложений${if (failed > 0) ", ошибок: $failed" else ""} · ${backend.label}",
                )
            }
        }
    }

    private fun startAccessibilityBatch(apps: List<InstalledApp>) {
        runCatching {
            accessibilityCoordinator.beginBatch(
                apps.map { app ->
                    AccessibilityCacheBatchItem(
                        packageName = app.packageName,
                        appLabel = app.label,
                        estimatedBytes = app.cacheBytes ?: 0L,
                    )
                },
            )
        }.onSuccess { first ->
            _state.update {
                it.copy(
                    busyPackage = first.packageName,
                    accessibilityLaunchPackage = first.packageName,
                    batchRunning = true,
                    batchCompleted = 0,
                    batchTotal = apps.size,
                    batchFailed = 0,
                    batchMessage = "Открываем ${first.appLabel}",
                    message = "ClearUp пройдёт выбранные приложения по очереди через системную кнопку «Очистить кэш».",
                    error = null,
                )
            }
        }.onFailure { error ->
            _state.update {
                it.copy(error = error.message ?: "Не удалось запустить пакетную Accessibility-очистку")
            }
        }
    }

    private suspend fun handleAccessibilityBatchResult(batch: AccessibilityCacheBatch) {
        if (batch.active || batch.historyRecorded) return
        try {
            if (batch.completedCount > 0) {
                history.record(
                    type = HistoryType.APP_ACTION,
                    itemCount = batch.completedCount,
                    bytes = batch.clearedEstimateBytes,
                    note = "ACCESSIBILITY:BATCH_CLEAR_CACHE:${batch.stage.name}",
                )
            }
            val refreshedApps = if (_state.value.allApps.isEmpty()) {
                _state.value.allApps
            } else {
                repository.loadApps(_state.value.includeSystemApps)
            }
            _state.update {
                it.copy(
                    allApps = refreshedApps,
                    selectedPackages = emptySet(),
                    busyPackage = null,
                    batchRunning = false,
                    batchCompleted = batch.completedCount,
                    batchTotal = batch.totalCount,
                    batchFailed = batch.failedCount,
                    batchMessage = null,
                    message = when (batch.stage) {
                        AccessibilityBatchStage.COMPLETED -> "Готово: кэш очищен у ${batch.completedCount} приложений · Спецвозможности"
                        AccessibilityBatchStage.CANCELLED -> batch.message
                        AccessibilityBatchStage.FAILED -> null
                        AccessibilityBatchStage.RUNNING -> null
                    },
                    error = if (batch.stage == AccessibilityBatchStage.FAILED) batch.message else null,
                )
            }
        } finally {
            accessibilityCoordinator.markBatchHistoryRecorded(batch.id)
        }
    }

    private suspend fun handleLegacyAccessibilityResult(session: AccessibilityCacheSession?) {
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
                }
                AccessibilityCacheStage.FAILED -> _state.update { it.copy(error = session.message) }
                AccessibilityCacheStage.CANCELLED -> _state.update { it.copy(message = session.message) }
                AccessibilityCacheStage.WAITING_APP_DETAILS,
                AccessibilityCacheStage.WAITING_STORAGE_PAGE,
                -> Unit
            }
        } finally {
            accessibilityCoordinator.markHistoryRecorded(session.id)
        }
    }

    private fun actionSuccessMessage(action: AppMaintenanceAction, backend: AppActionBackend): String {
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

    companion object {
        private const val MAX_BATCH_ITEMS = 100
    }
}
