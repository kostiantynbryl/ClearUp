package com.norvexa.clearup.data.scanner.rules

import com.norvexa.clearup.data.scanner.MediaEntry
import com.norvexa.clearup.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TemporaryFileRuleTest {
    private val rule = TemporaryFileRule()

    @Test
    fun tempInsideCacheIsSafe() {
        assertEquals(
            RiskLevel.SAFE,
            rule.evaluate(entry("work.tmp", "Android/media/app/cache/"))?.riskLevel,
        )
    }

    @Test
    fun backupInDownloadsRequiresReview() {
        assertEquals(
            RiskLevel.REVIEW,
            rule.evaluate(entry("notes.bak", "Download/"))?.riskLevel,
        )
    }

    @Test
    fun regularDocumentIsIgnored() {
        assertNull(rule.evaluate(entry("notes.txt", "Download/")))
    }

    private fun entry(name: String, path: String) = MediaEntry(
        id = 1,
        displayName = name,
        relativePath = path,
        uri = "content://test/1",
        bytes = 100,
        mimeType = "application/octet-stream",
        modifiedAtMillis = 1,
    )
}
