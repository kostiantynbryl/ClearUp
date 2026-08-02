package com.norvexa.clearup.data.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCachePolicyTest {
    @Test
    fun acceptsExactLocalizedClearCacheLabels() {
        assertTrue(AccessibilityCachePolicy.isExactClearCacheLabel("Clear cache"))
        assertTrue(AccessibilityCachePolicy.isExactClearCacheLabel("Очистить кэш"))
        assertTrue(AccessibilityCachePolicy.isExactClearCacheLabel("Очистити кеш"))
        assertTrue(AccessibilityCachePolicy.isExactClearCacheLabel("Wyczyść pamięć podręczną"))
    }

    @Test
    fun rejectsDangerousStorageAndDataActions() {
        assertFalse(AccessibilityCachePolicy.isExactClearCacheLabel("Clear storage"))
        assertFalse(AccessibilityCachePolicy.isExactClearCacheLabel("Стереть данные"))
        assertTrue(AccessibilityCachePolicy.isDangerousLabel("Clear data"))
        assertTrue(AccessibilityCachePolicy.isDangerousLabel("Очистити сховище"))
    }

    @Test
    fun acceptsOnlyAllowlistedSettingsPackages() {
        assertTrue(AccessibilityCachePolicy.isAllowedSettingsPackage("com.android.settings"))
        assertTrue(AccessibilityCachePolicy.isAllowedSettingsPackage("com.miui.securitycenter"))
        assertFalse(AccessibilityCachePolicy.isAllowedSettingsPackage("com.example.fake.settings"))
    }

    @Test
    fun acceptsOnlyNamedSafeResourceIds() {
        assertTrue(
            AccessibilityCachePolicy.isClearCacheViewId(
                "com.android.settings:id/clear_cache_button",
            ),
        )
        assertTrue(
            AccessibilityCachePolicy.isStorageViewId(
                "com.android.settings:id/storage_settings",
            ),
        )
        assertFalse(AccessibilityCachePolicy.isClearCacheViewId("com.android.settings:id/button2"))
        assertFalse(AccessibilityCachePolicy.isStorageViewId("com.android.settings:id/clear_data"))
    }

    @Test
    fun validatesTargetPackageNames() {
        assertTrue(AccessibilityCachePolicy.isValidPackageName("com.norvexa.example"))
        assertFalse(AccessibilityCachePolicy.isValidPackageName("com.example.app;rm"))
        assertFalse(AccessibilityCachePolicy.isValidPackageName("../data/local/tmp"))
    }
}
