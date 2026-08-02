package com.norvexa.clearup.domain.model

data class DuplicateItem(
    val id: Long,
    val uri: String,
    val displayName: String,
    val path: String,
    val bytes: Long,
    val modifiedAtMillis: Long,
    val selected: Boolean = false,
)

data class DuplicateGroup(
    val fingerprint: String,
    val items: List<DuplicateItem>,
) {
    val reclaimableBytes: Long = items.drop(1).sumOf { it.bytes }
}

data class DuplicateScanResult(
    val groups: List<DuplicateGroup>,
    val candidateCount: Int,
) {
    val duplicateCount: Int = groups.sumOf { (it.items.size - 1).coerceAtLeast(0) }
    val reclaimableBytes: Long = groups.sumOf { it.reclaimableBytes }
}
