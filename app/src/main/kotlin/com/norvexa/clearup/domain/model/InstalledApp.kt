package com.norvexa.clearup.domain.model

data class InstalledApp(
    val label: String,
    val packageName: String,
    val versionName: String,
    val installedAtMillis: Long,
    val updatedAtMillis: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val apkBytes: Long,
    val appBytes: Long? = null,
    val dataBytes: Long? = null,
    val cacheBytes: Long? = null,
)
