package com.norvexa.clearup.core.util

import java.text.DateFormat
import java.util.Date

object DateFormatter {
    fun format(timestampMillis: Long): String = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
    ).format(Date(timestampMillis))
}
