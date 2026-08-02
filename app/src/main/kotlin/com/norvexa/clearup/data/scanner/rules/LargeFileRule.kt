package com.norvexa.clearup.data.scanner.rules

import com.norvexa.clearup.data.scanner.MediaEntry
import com.norvexa.clearup.data.scanner.ScanRule
import com.norvexa.clearup.domain.model.CleanerCategory
import com.norvexa.clearup.domain.model.RiskLevel
import com.norvexa.clearup.domain.model.ScanItem

class LargeFileRule(private val thresholdBytes: Long) : ScanRule {
    override fun evaluate(entry: MediaEntry): ScanItem? {
        if (entry.bytes < thresholdBytes) return null
        return ScanItem(
            id = entry.id,
            displayName = entry.displayName,
            path = entry.relativePath,
            uri = entry.uri,
            bytes = entry.bytes,
            category = CleanerCategory.LARGE_FILES,
            riskLevel = RiskLevel.CAUTION,
            reason = "Крупный файл — удаляйте только после проверки",
            modifiedAtMillis = entry.modifiedAtMillis,
        )
    }
}
