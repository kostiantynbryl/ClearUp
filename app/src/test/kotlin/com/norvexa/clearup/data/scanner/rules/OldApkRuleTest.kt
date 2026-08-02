package com.norvexa.clearup.data.scanner.rules

import com.norvexa.clearup.data.scanner.MediaEntry
import com.norvexa.clearup.domain.model.CleanerCategory
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OldApkRuleTest {
    private val now = 1_800_000_000_000L
    private val rule = OldApkRule(nowMillis = { now }, minimumAgeDays = 7)

    @Test fun matchesOldApk() {
        val result = rule.evaluate(entry(modified = now - TimeUnit.DAYS.toMillis(8)))
        assertEquals(CleanerCategory.OLD_APK, result?.category)
    }

    @Test fun ignoresFreshApk() {
        assertNull(rule.evaluate(entry(modified = now - TimeUnit.DAYS.toMillis(2))))
    }

    private fun entry(modified: Long) = MediaEntry(
        id = 1,
        displayName = "app.apk",
        relativePath = "Download/",
        uri = "content://test/1",
        bytes = 100,
        mimeType = "application/vnd.android.package-archive",
        modifiedAtMillis = modified,
    )
}
