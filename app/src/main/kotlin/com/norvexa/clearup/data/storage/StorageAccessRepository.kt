package com.norvexa.clearup.data.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

class StorageAccessException(message: String) : SecurityException(message)

data class StorageAccessState(
    val completeAccess: Boolean,
    val allFilesGranted: Boolean,
    val legacyReadGranted: Boolean,
    val requiresAllFilesAccess: Boolean,
    val requiresLegacyReadPermission: Boolean,
)

class StorageAccessRepository(context: Context) {
    private val appContext = context.applicationContext

    fun readState(): StorageAccessState {
        val legacyReadGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        val allFilesGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            Environment.isExternalStorageManager()
        return StorageAccessState(
            completeAccess = legacyReadGranted && allFilesGranted,
            allFilesGranted = allFilesGranted,
            legacyReadGranted = legacyReadGranted,
            requiresAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !allFilesGranted,
            requiresLegacyReadPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.R && !legacyReadGranted,
        )
    }

    fun requireCompleteAccess() {
        val state = readState()
        if (!state.completeAccess) {
            throw StorageAccessException(
                if (state.requiresAllFilesAccess) {
                    "Для полного сканирования включите «Доступ ко всем файлам» для ClearUp"
                } else {
                    "Разрешите ClearUp читать файлы устройства"
                },
            )
        }
    }

    fun createAllFilesAccessIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${appContext.packageName}"),
    )

    fun createAllFilesFallbackIntent(): Intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}
