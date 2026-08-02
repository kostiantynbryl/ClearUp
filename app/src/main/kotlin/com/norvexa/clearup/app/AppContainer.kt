package com.norvexa.clearup.app

import android.content.Context
import com.norvexa.clearup.data.apps.AndroidAppRepository
import com.norvexa.clearup.data.cleanup.TrashManager
import com.norvexa.clearup.data.scanner.ScannerEngine
import com.norvexa.clearup.data.settings.SettingsRepository
import com.norvexa.clearup.data.storage.AndroidStorageRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsRepository = SettingsRepository(appContext)
    val storageRepository = AndroidStorageRepository(appContext)
    val appRepository = AndroidAppRepository(appContext)
    val scannerEngine = ScannerEngine(appContext)
    val trashManager = TrashManager(appContext)
}
