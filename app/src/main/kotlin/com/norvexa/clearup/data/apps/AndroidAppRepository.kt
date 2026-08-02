package com.norvexa.clearup.data.apps

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import com.norvexa.clearup.domain.model.InstalledApp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAppRepository(private val context: Context) {
    suspend fun loadApps(includeSystemApps: Boolean): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        @Suppress("DEPRECATION")
        val packages = packageManager.getInstalledPackages(0)
        val statsManager = context.getSystemService(StorageStatsManager::class.java)
        val userHandle = Process.myUserHandle()

        packages.mapNotNull { packageInfo ->
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            val isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
            if (!includeSystemApps && isSystem) return@mapNotNull null
            val stats = runCatching {
                statsManager.queryStatsForPackage(StorageManager.UUID_DEFAULT, packageInfo.packageName, userHandle)
            }.getOrNull()
            InstalledApp(
                label = appInfo.loadLabel(packageManager).toString(),
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName.orEmpty(),
                installedAtMillis = packageInfo.firstInstallTime,
                updatedAtMillis = packageInfo.lastUpdateTime,
                isSystem = isSystem,
                apkBytes = runCatching { File(appInfo.sourceDir).length() }.getOrDefault(0),
                appBytes = stats?.appBytes,
                dataBytes = stats?.dataBytes,
                cacheBytes = stats?.cacheBytes,
            )
        }.sortedBy { it.label.lowercase() }
    }
}
