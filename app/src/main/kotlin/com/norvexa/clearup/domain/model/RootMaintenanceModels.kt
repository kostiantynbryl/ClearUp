package com.norvexa.clearup.domain.model

enum class OrphanLocation(val displayName: String) {
    INTERNAL_USER("Данные пользователя"),
    INTERNAL_LEGACY("Старые внутренние данные"),
    EXTERNAL_DATA("Android/data"),
    EXTERNAL_MEDIA("Android/media"),
    EXTERNAL_OBB("Android/obb"),
}

data class OrphanDirectory(
    val packageName: String,
    val path: String,
    val canonicalPath: String,
    val bytes: Long,
    val modifiedAtMillis: Long,
    val location: OrphanLocation,
    val selected: Boolean = false,
)

data class RootAuditEntry(
    val id: Long,
    val action: String,
    val target: String,
    val success: Boolean,
    val exitCode: Int,
    val outputPreview: String,
    val errorPreview: String,
    val createdAtMillis: Long,
)
