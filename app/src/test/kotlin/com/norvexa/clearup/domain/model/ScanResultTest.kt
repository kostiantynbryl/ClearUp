package com.norvexa.clearup.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ScanResultTest {
    @Test
    fun reclaimableBytesCountsOnlySafeItems() {
        val result = ScanResult(
            items = listOf(
                item(1, 1_000, CleanerCategory.TEMPORARY, RiskLevel.SAFE),
                item(2, 5_000, CleanerCategory.SCREENSHOTS, RiskLevel.REVIEW),
                item(3, 9_000, CleanerCategory.LARGE_FILES, RiskLevel.CAUTION),
            ),
            scannedCount = 3,
            startedAtMillis = 1,
            completedAtMillis = 2,
        )

        assertEquals(1_000L, result.reclaimableBytes)
        assertEquals(14_000L, result.reviewBytes)
    }

    private fun item(
        id: Long,
        bytes: Long,
        category: CleanerCategory,
        risk: RiskLevel,
    ) = ScanItem(
        id = id,
        displayName = "item-$id",
        path = "Download/",
        uri = "content://test/$id",
        bytes = bytes,
        category = category,
        riskLevel = risk,
        reason = "test",
        modifiedAtMillis = 0,
    )
}
