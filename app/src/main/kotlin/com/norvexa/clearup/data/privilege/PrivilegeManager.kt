package com.norvexa.clearup.data.privilege

import android.content.Context
import android.content.pm.PackageManager
import com.norvexa.clearup.domain.model.PrivilegeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class PrivilegeManager(
    private val context: Context,
    private val rootShell: RootShell,
) {
    @Volatile
    private var cachedRootAvailable: Boolean? = null

    suspend fun readState(checkRoot: Boolean = false): PrivilegeState = withContext(Dispatchers.IO) {
        val installed = runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        }.isSuccess
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permissionGranted = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = if (running) {
            runCatching { Shizuku.getUid() }.getOrNull()
        } else {
            null
        }
        val rootAvailable = if (checkRoot) {
            rootShell.isAvailable().also { cachedRootAvailable = it }
        } else {
            cachedRootAvailable == true
        }

        PrivilegeState(
            rootAvailable = rootAvailable,
            shizukuInstalled = installed,
            shizukuRunning = running,
            shizukuPermissionGranted = permissionGranted,
            shizukuUid = uid,
        )
    }

    fun requestShizukuPermission(requestCode: Int = SHIZUKU_PERMISSION_REQUEST) {
        if (
            Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
        ) {
            Shizuku.requestPermission(requestCode)
        }
    }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_PERMISSION_REQUEST = 4317
    }
}
