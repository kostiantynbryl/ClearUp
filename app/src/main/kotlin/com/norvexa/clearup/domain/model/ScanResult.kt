package com.norvexa.clearup.domain.model

data class ScanResult(
    val items: List<ScanItem>,
    val scannedCount: Int,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
) {
    val reclaimableBytes: Long = items
        .asSequence()
        .filter { it.riskLevel == RiskLevel.SAFE }
        .sumOf { it.bytes }

    val reviewBytes: Long = items
        .asSequence()
        .filter { it.riskLevel != RiskLevel.SAFE }
        .sumOf { it.bytes }
}
