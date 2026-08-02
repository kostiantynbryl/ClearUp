package com.norvexa.clearup.domain.model

data class ReleaseUpdate(
    val tag: String,
    val title: String,
    val notes: String,
    val publishedAt: String,
    val apkName: String,
    val apkUrl: String,
    val checksumUrl: String,
)
