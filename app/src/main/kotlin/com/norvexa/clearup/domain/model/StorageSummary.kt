package com.norvexa.clearup.domain.model

data class StorageSummary(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
) {
    val usedFraction: Float = if (totalBytes <= 0) 0f else (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)

    companion object {
        val Empty = StorageSummary(0, 0, 0)
    }
}
