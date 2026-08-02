package com.norvexa.clearup.domain.model

data class PrivilegeState(
    val rootAvailable: Boolean = false,
    val shizukuInstalled: Boolean = false,
    val shizukuRunning: Boolean = false,
    val shizukuPermissionGranted: Boolean = false,
    val shizukuUid: Int? = null,
) {
    val bestMode: AccessMode = when {
        rootAvailable -> AccessMode.ROOT
        shizukuRunning && shizukuPermissionGranted -> AccessMode.SHIZUKU
        else -> AccessMode.STANDARD
    }
}
