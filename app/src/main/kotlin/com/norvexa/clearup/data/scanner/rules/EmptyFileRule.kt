package com.norvexa.clearup.data.scanner.rules

import com.norvexa.clearup.data.scanner.MediaEntry
import com.norvexa.clearup.data.scanner.ScanRule
import com.norvexa.clearup.domain.model.CleanerCategory
import com.norvexa.clearup.domain.model.RiskLevel
import com.norvexa.clearup.domain.model.ScanItem
import java.util.concurrent.TimeUnit

class EmptyFileRule(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val minimumAgeDays: Long = 30,
) : ScanRule {
    override fun evaluate(entry: MediaEntry): ScanItem? {
        if (entry.bytes != 0L) return null
        if (entry.displayName.startsWith(".") && entry.displayName.length <= 2) return null
        val age = nowMillis() - entry.modifiedAtMillis
        if (age < TimeUnit.DAYS.toMillis(minimumAgeDays)) return null

        return ScanItem(
            id = entry.id,
            displayName = entry.displayName,
            path = entry.relativePath,
            uri = entry.uri,
            bytes = 0,
            category = CleanerCategory.EMPTY_FILES,
            riskLevel = RiskLevel.REVIEW,
            reason = "Пустой файл старше $minimumAgeDays дней",
            modifiedAtMillis = entry.modifiedAtMillis,
        )
    }
}
