package com.norvexa.clearup.app

import android.content.Context
import com.norvexa.clearup.automation.AutomationScheduler
import com.norvexa.clearup.data.apps.AndroidAppRepository
import com.norvexa.clearup.data.cleanup.TrashManager
import com.norvexa.clearup.data.duplicates.DuplicateRepository
import com.norvexa.clearup.data.exclusions.ExclusionRepository
import com.norvexa.clearup.data.history.HistoryStore
import com.norvexa.clearup.data.privilege.PrivilegeManager
import com.norvexa.clearup.data.privilege.RootAuditStore
import com.norvexa.clearup.data.privilege.RootOrphanRepository
import com.norvexa.clearup.data.privilege.RootShell
import com.norvexa.clearup.data.scanner.ScannerEngine
import com.norvexa.clearup.data.settings.SettingsRepository
import com.norvexa.clearup.data.storage.AndroidStorageRepository
import com.norvexa.clearup.data.update.UpdateRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val packageName: String = appContext.packageName
    val settingsRepository = SettingsRepository(appContext)
    val exclusionRepository = ExclusionRepository(appContext)
    val historyStore = HistoryStore(appContext)
    val storageRepository = AndroidStorageRepository(appContext)
    val appRepository = AndroidAppRepository(appContext)
    val scannerEngine = ScannerEngine(appContext)
    val duplicateRepository = DuplicateRepository(appContext)
    val trashManager = TrashManager(appContext)
    val rootAuditStore = RootAuditStore(appContext)
    val rootShell = RootShell(rootAuditStore)
    val rootOrphanRepository = RootOrphanRepository(appContext, rootShell)
    val privilegeManager = PrivilegeManager(appContext, rootShell)
    val automationScheduler = AutomationScheduler(appContext)
    val updateRepository = UpdateRepository(appContext)
}
