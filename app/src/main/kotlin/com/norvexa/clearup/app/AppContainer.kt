package com.norvexa.clearup.app

import android.content.Context
import com.norvexa.clearup.automation.AutomationScheduler
import com.norvexa.clearup.data.accessibility.AccessibilityCacheCoordinator
import com.norvexa.clearup.data.apps.AndroidAppRepository
import com.norvexa.clearup.data.cleanup.TrashManager
import com.norvexa.clearup.data.directories.EmptyDirectoryRepository
import com.norvexa.clearup.data.duplicates.DuplicateRepository
import com.norvexa.clearup.data.exclusions.ExclusionRepository
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.data.privilege.PrivilegeManager
import com.norvexa.clearup.data.privilege.RootAuditStore
import com.norvexa.clearup.data.privilege.RootOrphanRepository
import com.norvexa.clearup.data.privilege.RootShell
import com.norvexa.clearup.data.privilege.ShizukuAuditStore
import com.norvexa.clearup.data.privilege.ShizukuCommandClient
import com.norvexa.clearup.data.report.ReportExporter
import com.norvexa.clearup.data.scanner.ScannerEngine
import com.norvexa.clearup.data.settings.SettingsRepository
import com.norvexa.clearup.data.storage.AndroidStorageRepository
import com.norvexa.clearup.data.storage.StorageAccessRepository
import com.norvexa.clearup.data.update.UpdateRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val packageName: String = appContext.packageName
    val settingsRepository = SettingsRepository(appContext)
    val exclusionRepository = ExclusionRepository(appContext)
    val historyStore = HistoryStore(appContext)
    val reportExporter = ReportExporter(appContext)
    val storageRepository = AndroidStorageRepository(appContext)
    val storageAccessRepository = StorageAccessRepository(appContext)
    val appRepository = AndroidAppRepository(appContext)
    val scannerEngine = ScannerEngine(appContext, storageAccessRepository)
    val duplicateRepository = DuplicateRepository(appContext)
    val emptyDirectoryRepository = EmptyDirectoryRepository(appContext)
    val trashManager = TrashManager(appContext)
    val accessibilityCacheCoordinator = AccessibilityCacheCoordinator(appContext)
    val rootAuditStore = RootAuditStore(appContext)
    val rootShell = RootShell(rootAuditStore)
    val rootOrphanRepository = RootOrphanRepository(appContext, rootShell)
    val shizukuAuditStore = ShizukuAuditStore(appContext)
    val shizukuCommandClient = ShizukuCommandClient(appContext, shizukuAuditStore)
    val privilegeManager = PrivilegeManager(appContext, rootShell)
    val automationScheduler = AutomationScheduler(appContext)
    val updateRepository = UpdateRepository(appContext)
}
