package com.norvexa.clearup.data.settings

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences ->
            AppSettings(
                themeMode = ThemeMode.fromStorage(preferences[Keys.themeMode]),
                safeMode = preferences[Keys.safeMode] ?: true,
                includeSystemApps = preferences[Keys.includeSystemApps] ?: false,
                largeFileThresholdMb = preferences[Keys.largeFileThresholdMb] ?: 100,
                automaticScanEnabled = preferences[Keys.autoEnabled] ?: false,
                automaticScanIntervalDays = preferences[Keys.autoDays] ?: 7,
                automaticScanChargingOnly = preferences[Keys.autoCharging] ?: true,
            )
        }

    suspend fun setThemeMode(value: ThemeMode) = edit { it[Keys.themeMode] = value.storageKey }
    suspend fun setSafeMode(value: Boolean) = edit { it[Keys.safeMode] = value }
    suspend fun setIncludeSystemApps(value: Boolean) = edit { it[Keys.includeSystemApps] = value }
    suspend fun setLargeFileThresholdMb(value: Int) = edit {
        it[Keys.largeFileThresholdMb] = value.coerceIn(20, 4096)
    }
    suspend fun setAutomaticScanEnabled(value: Boolean) = edit { it[Keys.autoEnabled] = value }
    suspend fun setAutomaticScanIntervalDays(value: Int) = edit {
        it[Keys.autoDays] = value.coerceIn(1, 30)
    }
    suspend fun setAutomaticScanChargingOnly(value: Boolean) = edit {
        it[Keys.autoCharging] = value
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.clearUpDataStore.edit(block)
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val safeMode = booleanPreferencesKey("safe_mode")
        val includeSystemApps = booleanPreferencesKey("include_system_apps")
        val largeFileThresholdMb = intPreferencesKey("large_file_threshold_mb")
        val autoEnabled = booleanPreferencesKey("automatic_scan_enabled")
        val autoDays = intPreferencesKey("automatic_scan_interval_days")
        val autoCharging = booleanPreferencesKey("automatic_scan_charging_only")
    }
}
