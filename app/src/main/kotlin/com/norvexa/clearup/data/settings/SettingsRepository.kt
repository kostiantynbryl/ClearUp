package com.norvexa.clearup.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.norvexa.clearup.core.theme.ThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.clearUpDataStore by preferencesDataStore(name = "clearup_settings")

class SettingsRepository(private val context: Context) {
    val defaultSettings = AppSettings()

    val settings: Flow<AppSettings> = context.clearUpDataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                themeMode = ThemeMode.fromStorage(preferences[Keys.themeMode]),
                safeMode = preferences[Keys.safeMode] ?: true,
                includeSystemApps = preferences[Keys.includeSystemApps] ?: false,
                largeFileThresholdMb = preferences[Keys.largeFileThresholdMb] ?: 100,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.clearUpDataStore.edit { it[Keys.themeMode] = mode.storageKey }
    }

    suspend fun setSafeMode(enabled: Boolean) {
        context.clearUpDataStore.edit { it[Keys.safeMode] = enabled }
    }

    suspend fun setIncludeSystemApps(enabled: Boolean) {
        context.clearUpDataStore.edit { it[Keys.includeSystemApps] = enabled }
    }

    suspend fun setLargeFileThresholdMb(value: Int) {
        context.clearUpDataStore.edit { it[Keys.largeFileThresholdMb] = value.coerceIn(20, 4096) }
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val safeMode = booleanPreferencesKey("safe_mode")
        val includeSystemApps = booleanPreferencesKey("include_system_apps")
        val largeFileThresholdMb = intPreferencesKey("large_file_threshold_mb")
    }
}
