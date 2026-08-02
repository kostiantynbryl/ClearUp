package com.norvexa.clearup.data.scanner

data class MediaEntry(
    val id: Long,
    val displayName: String,
    val relativePath: String,
    val uri: String,
    val bytes: Long,
    val mimeType: String,
    val modifiedAtMillis: Long,
)
