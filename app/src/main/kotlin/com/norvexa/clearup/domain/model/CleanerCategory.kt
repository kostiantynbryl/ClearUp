package com.norvexa.clearup.domain.model

enum class CleanerCategory(val displayName: String) {
    TEMPORARY("Временные файлы"),
    OLD_APK("Старые APK"),
    SCREENSHOTS("Скриншоты"),
    LARGE_FILES("Крупные файлы"),
    EMPTY_FILES("Пустые файлы"),
    DOWNLOADS("Загрузки"),
    OTHER("Другое"),
}
