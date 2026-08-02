package com.norvexa.clearup.data.scanner.rules

import com.norvexa.clearup.data.scanner.MediaEntry
import com.norvexa.clearup.data.scanner.ScanRule
import com.norvexa.clearup.domain.model.CleanerCategory
import com.norvexa.clearup.domain.model.RiskLevel
import com.norvexa.clearup.domain.model.ScanItem

class TemporaryFileRule : ScanRule {
    private val temporarySuffixes = setOf(".tmp", ".temp", ".log", ".bak", ".old", ".cache")
    private val exactNames = setOf("thumbs.db", ".ds_store")

    override fun evaluate(entry: MediaEntry): ScanItem? {
        val lower = entry.displayName.lowercase()
        if (exactNames.none { it == lower } && temporarySuffixes.none { lower.endsWith(it) }) return null
        return ScanItem(
            id = entry.id,
            displayName = entry.displayName,
            path = entry.relativePath,
            uri = entry.uri,
            bytes = entry.bytes,
            category = CleanerCategory.TEMPORARY,
            riskLevel = RiskLevel.SAFE,
            reason = "Типичный временный или служебный файл",
            modifiedAtMillis = entry.modifiedAtMillis,
            selected = true,
        )
    }
}
