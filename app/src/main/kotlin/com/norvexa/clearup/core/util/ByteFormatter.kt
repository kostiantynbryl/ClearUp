package com.norvexa.clearup.core.util

import java.util.Locale
import kotlin.math.abs

object ByteFormatter {
    fun format(bytes: Long): String {
        if (abs(bytes) < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (abs(value) >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        val pattern = if (abs(value) >= 100) "%.0f %s" else "%.1f %s"
        return String.format(Locale.getDefault(), pattern, value, units[unitIndex])
    }
}
