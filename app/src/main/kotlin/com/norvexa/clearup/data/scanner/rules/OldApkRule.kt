package com.norvexa.clearup.data.scanner.rules

import com.norvexa.clearup.data.scanner.MediaEntry
import com.norvexa.clearup.data.scanner.ScanRule
import com.norvexa.clearup.domain.model.CleanerCategory
import com.norvexa.clearup.domain.model.RiskLevel
import com.norvexa.clearup.domain.model.ScanItem
import java.util.concurrent.TimeUnit

class OldApkRule(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val minimumAgeDays: Long = 7,
) : ScanRule {
    override fun evaluate(entry: MediaEntry): ScanItem? {
        val isApk = entry.mimeType == "application/vnd.android.package-archive" || entry.displayName.endsWith(".apk", ignoreCase = true)
        val ageMillis = nowMillis() - entry.modifiedAtMillis
        if (!isApk || ageMillis < TimeUnit.DAYS.toMillis(minimumAgeDays)) return null
        return ScanItem(
            id = entry.id,
            displayName = entry.displayName,
            path = entry.relativePath,
            uri = entry.uri,
            bytes = entry.bytes,
            category = CleanerCategory.OLD_APK,
            riskLevel = RiskLevel.REVIEW,
            reason = "Установочный APK старше $minimumAgeDays дней",
            modifiedAtMillis = entry.modifiedAtMillis,
        )
    }
}
