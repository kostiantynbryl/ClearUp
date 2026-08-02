package com.norvexa.clearup.data.scanner.rules

import com.norvexa.clearup.data.scanner.MediaEntry
import com.norvexa.clearup.data.scanner.ScanRule
import com.norvexa.clearup.domain.model.CleanerCategory
import com.norvexa.clearup.domain.model.RiskLevel
import com.norvexa.clearup.domain.model.ScanItem

class ScreenshotRule : ScanRule {
    override fun evaluate(entry: MediaEntry): ScanItem? {
        val looksLikeScreenshot = entry.relativePath.contains("screenshot", ignoreCase = true) ||
            entry.displayName.contains("screenshot", ignoreCase = true)
        if (!looksLikeScreenshot) return null
        return ScanItem(
            id = entry.id,
            displayName = entry.displayName,
            path = entry.relativePath,
            uri = entry.uri,
            bytes = entry.bytes,
            category = CleanerCategory.SCREENSHOTS,
            riskLevel = RiskLevel.REVIEW,
            reason = "Файл расположен в папке скриншотов",
            modifiedAtMillis = entry.modifiedAtMillis,
        )
    }
}
