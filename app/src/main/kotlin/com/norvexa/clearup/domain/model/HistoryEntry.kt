package com.norvexa.clearup.domain.model

enum class HistoryType {
    SCAN,
    CLEANUP,
    AUTOMATION,
    APP_ACTION,
    UPDATE,
}

data class HistoryEntry(
    val id: Long,
    val type: HistoryType,
    val itemCount: Int,
    val bytes: Long,
    val note: String,
    val createdAtMillis: Long,
)
