package com.norvexa.clearup.domain.model

data class ScanItem(
    val id: Long,
    val displayName: String,
    val path: String,
    val uri: String,
    val bytes: Long,
    val category: CleanerCategory,
    val riskLevel: RiskLevel,
    val reason: String,
    val modifiedAtMillis: Long,
    val selected: Boolean = false,
)
