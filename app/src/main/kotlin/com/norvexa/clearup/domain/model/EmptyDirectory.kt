package com.norvexa.clearup.domain.model

data class EmptyDirectory(
    val path: String,
    val canonicalPath: String,
    val modifiedAtMillis: Long,
    val depth: Int,
)

data class EmptyDirectoryDeleteResult(
    val deleted: Int,
    val skipped: Int,
    val failedPaths: List<String>,
)
