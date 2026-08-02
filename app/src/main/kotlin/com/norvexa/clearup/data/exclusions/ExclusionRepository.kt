package com.norvexa.clearup.data.exclusions

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.exclusionDataStore by preferencesDataStore(name = "clearup_exclusions")

data class Exclusions(
    val pathPrefixes: Set<String> = emptySet(),
    val packages: Set<String> = emptySet(),
)

class ExclusionRepository(private val context: Context) {
    val exclusions: Flow<Exclusions> = context.exclusionDataStore.data.map { preferences ->
        Exclusions(
            pathPrefixes = preferences[Keys.paths].orEmpty(),
            packages = preferences[Keys.packages].orEmpty(),
        )
    }

    suspend fun current(): Exclusions = exclusions.first()

    suspend fun addPath(path: String) = update(Keys.paths, path)
    suspend fun removePath(path: String) = remove(Keys.paths, path)
    suspend fun addPackage(packageName: String) = update(Keys.packages, packageName)
    suspend fun removePackage(packageName: String) = remove(Keys.packages, packageName)

    private suspend fun update(
        key: Preferences.Key<Set<String>>,
        value: String,
    ) {
        val normalized = value.trim()
        if (normalized.isEmpty()) return
        context.exclusionDataStore.edit { preferences ->
            preferences[key] = preferences[key].orEmpty() + normalized
        }
    }

    private suspend fun remove(
        key: Preferences.Key<Set<String>>,
        value: String,
    ) {
        context.exclusionDataStore.edit { preferences ->
            preferences[key] = preferences[key].orEmpty() - value
        }
    }

    private object Keys {
        val paths = stringSetPreferencesKey("excluded_paths")
        val packages = stringSetPreferencesKey("excluded_packages")
    }
}
