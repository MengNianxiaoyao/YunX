package com.yunx.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun formatsCommonFileTimes() {
        assertEquals("2024-01-02 03:04", formatFileModifyTime("2024-01-02 03:04:05"))
        assertEquals("2024-01-02 03:04", formatFileModifyTime("2024-01-02T03:04:05"))
    }

    @Test
    fun formatsEpochSecondsAndMillis() {
        val expectedPattern = Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")
        assert(expectedPattern.matches(formatFileModifyTime("1704164640000")))
        assert(expectedPattern.matches(formatFileModifyTime("1704164640")))
    }
}
