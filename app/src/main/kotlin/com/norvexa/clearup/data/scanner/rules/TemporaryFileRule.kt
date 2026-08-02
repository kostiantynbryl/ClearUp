package com.norvexa.clearup.data.scanner.rules

import com.norvexa.clearup.data.scanner.MediaEntry
import com.norvexa.clearup.data.scanner.ScanRule
import com.norvexa.clearup.domain.model.CleanerCategory
import com.norvexa.clearup.domain.model.RiskLevel
import com.norvexa.clearup.domain.model.ScanItem

class TemporaryFileRule : ScanRule {
    private val temporarySuffixes = setOf(".tmp", ".temp", ".cache")
    private val reviewSuffixes = setOf(".log", ".bak", ".old")
    private val exactSafeNames = setOf("thumbs.db", ".ds_store")

    override fun evaluate(entry: MediaEntry): ScanItem? {
        val lowerName = entry.displayName.lowercase()
        val lowerPath = entry.relativePath.lowercase()
        val isThumbnail = ".thumbnails" in lowerPath || lowerName in exactSafeNames
        val isTemporary = temporarySuffixes.any(lowerName::endsWith)
        val requiresReview = reviewSuffixes.any(lowerName::endsWith)
        if (!isThumbnail && !isTemporary && !requiresReview) return null

        val inTemporaryDirectory = lowerPath.contains("/cache/") ||
            lowerPath.contains("/temp/") ||
            lowerPath.contains("/tmp/") ||
            lowerPath.startsWith("cache/") ||
            lowerPath.startsWith("temp/") ||
            lowerPath.startsWith("tmp/")
        val risk = if (isThumbnail || (isTemporary && inTemporaryDirectory)) {
            RiskLevel.SAFE
        } else {
            RiskLevel.REVIEW
        }

        return ScanItem(
            id = entry.id,
            displayName = entry.displayName,
            path = entry.relativePath,
            uri = entry.uri,
            bytes = entry.bytes,
            category = CleanerCategory.TEMPORARY,
            riskLevel = risk,
            reason = if (risk == RiskLevel.SAFE) {
                "Типичный временный файл или миниатюра"
            } else {
                "Расширение похоже на служебное, но файл требует ручной проверки"
            },
            modifiedAtMillis = entry.modifiedAtMillis,
        )
    }
}
