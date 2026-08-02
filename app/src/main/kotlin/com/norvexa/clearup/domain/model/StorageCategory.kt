package com.norvexa.clearup.domain.model

enum class StorageCategory(val displayName: String) {
    IMAGES("Фото"),
    VIDEO("Видео"),
    AUDIO("Аудио"),
    DOCUMENTS("Документы"),
    ARCHIVES("Архивы"),
    APK("APK"),
    OTHER("Другое"),
}

data class StorageCategoryUsage(
    val category: StorageCategory,
    val bytes: Long,
    val count: Int,
)
