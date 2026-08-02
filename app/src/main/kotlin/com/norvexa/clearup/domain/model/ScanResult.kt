package com.norvexa.clearup.domain.model

data class ScanResult(
    val items: List<ScanItem>,
    val scannedCount: Int,
    val startedAtMillis: Long,
    val completedAtMillis: Long,
) {
    val reclaimableBytes: Long = items.sumOf { it.bytes }
}
