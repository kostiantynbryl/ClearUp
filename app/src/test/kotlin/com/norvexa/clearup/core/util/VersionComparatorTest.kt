package com.norvexa.clearup.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun newerPatchWins() {
        assertTrue(VersionComparator.compare("v1.2.4", "1.2.3") > 0)
    }

    @Test
    fun stableWinsOverPrerelease() {
        assertTrue(VersionComparator.compare("1.0.0", "1.0.0-dev") > 0)
    }

    @Test
    fun equivalentVersionsMatch() {
        assertEquals(0, VersionComparator.compare("v2.1", "2.1.0"))
    }

    @Test
    fun malformedVersionDoesNotForceUpdate() {
        assertEquals(0, VersionComparator.compare("latest", "1.0.0"))
    }
}
