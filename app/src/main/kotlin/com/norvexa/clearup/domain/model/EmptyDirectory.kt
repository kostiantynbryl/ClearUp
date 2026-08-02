package com.norvexa.clearup.domain.model

data class EmptyDirectory(
    val path: String,
    val canonicalPath: String,
    val modifiedAtMillis: Long,
    val depth: Int,
)

data class EmptyDirectoryScanResult(
    val directories: List<EmptyDirectory>,
    val visitedDirectories: Int,
    val limitReached: Boolean,
)

data class EmptyDirectoryDeleteResult(
    val deleted: Int,
    val skipped: Int,
    val failedPaths: List<String>,
)
