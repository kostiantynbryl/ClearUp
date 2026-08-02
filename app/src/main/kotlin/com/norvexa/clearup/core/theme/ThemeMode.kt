package com.norvexa.clearup.core.theme

enum class ThemeMode(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED("amoled");

    companion object {
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}
