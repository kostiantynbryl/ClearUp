package com.norvexa.clearup.data.privilege

import com.norvexa.clearup.domain.model.OrphanLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootOrphanPolicyTest {
    @Test
    fun acceptsNormalPackageName() {
        assertTrue(RootOrphanPolicy.isValidPackageName("com.norvexa.example"))
    }

    @Test
    fun rejectsShellCharacters() {
        assertFalse(RootOrphanPolicy.isValidPackageName("com.example.app;rm"))
        assertFalse(RootOrphanPolicy.isValidPackageName("../data/local/tmp"))
        assertFalse(RootOrphanPolicy.isValidPackageName("singleword"))
    }

    @Test
    fun acceptsOnlyExactAllowlistedPath() {
        assertTrue(
            RootOrphanPolicy.isAllowedPath(
                "com.example.app",
                "/storage/emulated/0/Android/data/com.example.app",
            ),
        )
        assertFalse(
            RootOrphanPolicy.isAllowedPath(
                "com.example.app",
                "/storage/emulated/0/Download/com.example.app",
            ),
        )
        assertFalse(
            RootOrphanPolicy.isAllowedPath(
                "com.example.app",
                "/storage/emulated/0/Android/data/com.example.app/../../other",
            ),
        )
    }

    @Test
    fun mapsKnownRoot() {
        assertEquals(
            OrphanLocation.EXTERNAL_OBB,
            RootOrphanPolicy.locationForRoot("/storage/emulated/0/Android/obb"),
        )
    }
}
