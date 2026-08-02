package com.norvexa.clearup.data.settings

import com.norvexa.clearup.core.theme.ThemeMode

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val safeMode: Boolean = true,
    val includeSystemApps: Boolean = false,
    val largeFileThresholdMb: Int = 100,
    val automaticScanEnabled: Boolean = false,
    val automaticScanIntervalDays: Int = 7,
    val automaticScanChargingOnly: Boolean = true,
)
