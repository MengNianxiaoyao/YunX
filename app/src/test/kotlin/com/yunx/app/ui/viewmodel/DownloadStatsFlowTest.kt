package com.yunx.app.ui.viewmodel

import com.yunx.app.data.download.DownloadStats
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStatsFlowTest {
    @Test
    fun emitsOnlyWhenRequestedTaskChanges() = runBlocking {
        val first = DownloadStats(speed = 100, remainMillis = 1_000, chunkCount = 4)
        val second = DownloadStats(speed = 200, remainMillis = 500, chunkCount = 4)

        val values = flowOf(
            mapOf(1L to first),
            mapOf(1L to first, 2L to DownloadStats(speed = 999)),
            mapOf(1L to second),
            mapOf(2L to DownloadStats(speed = 999))
        ).statsForTask(1L).toList()

        assertEquals(listOf(first, second, null), values)
    }
}
