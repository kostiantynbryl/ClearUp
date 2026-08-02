package com.norvexa.clearup.data.scanner

import com.norvexa.clearup.domain.model.ScanItem

fun interface ScanRule {
    fun evaluate(entry: MediaEntry): ScanItem?
}
