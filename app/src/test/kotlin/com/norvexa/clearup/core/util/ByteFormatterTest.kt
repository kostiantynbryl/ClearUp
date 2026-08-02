package com.norvexa.clearup.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatterTest {
    @Test fun formatsBytes() { assertEquals("512 B", ByteFormatter.format(512)) }
    @Test fun formatsKilobytes() { assertEquals("1.0 KB", ByteFormatter.format(1024)) }
    @Test fun formatsMegabytes() { assertEquals("1.0 MB", ByteFormatter.format(1024L * 1024L)) }
}
