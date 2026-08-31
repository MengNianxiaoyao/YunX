package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTaskMetricTest {
    @Test
    fun usesMonotonicElapsedTimeAndClampsClockAnomalies() {
        assertEquals(250L, DownloadTaskMetric.elapsedMillis(1_000_000_000L, 1_250_000_000L))
        assertEquals(0L, DownloadTaskMetric.elapsedMillis(2_000L, 1_000L))
    }

    @Test
    fun formatsRetryWithoutSensitiveFields() {
        val metric = DownloadTaskMetric.retry(
            operationId = "download-0123456789abcdef0123456789abcdef",
            taskId = 42,
            platform = DownloadPlatform.QUARK,
            retry = 1,
            maxRetries = 3,
            failureKind = DownloadFailureKind.NETWORK,
            elapsedMillis = 1200
        )

        assertEquals(
            "metric=download_retry operationId=download-0123456789abcdef0123456789abcdef " +
                "taskId=42 platform=quark retry=1 maxRetries=3 " +
                "failureKind=network elapsedMs=1200",
            metric
        )
        assertFalse(metric.contains("url", ignoreCase = true))
        assertFalse(metric.contains("fileName", ignoreCase = true))
        assertFalse(metric.contains("cookie", ignoreCase = true))
    }

    @Test
    fun formatsTerminalOutcomeAndNormalizesUnknownPlatform() {
        val metric = DownloadTaskMetric.terminal(
            operationId = "download-0123456789abcdef0123456789abcdef",
            taskId = 7,
            platform = "secret-platform-value",
            outcome = DownloadMetricOutcome.FAILURE,
            retries = 2,
            elapsedMillis = 3000,
            failureKind = DownloadFailureKind.STORAGE
        )

        assertTrue(metric.contains("platform=generic"))
        assertTrue(metric.contains("outcome=failure"))
        assertTrue(metric.contains("retries=2"))
        assertTrue(metric.contains("failureKind=storage"))
        assertFalse(metric.contains("secret-platform-value"))
    }

    @Test
    fun formatsCancellationWithoutFailureKind() {
        val metric = DownloadTaskMetric.terminal(
            operationId = "download-0123456789abcdef0123456789abcdef",
            taskId = 8,
            platform = DownloadPlatform.UC,
            outcome = DownloadMetricOutcome.CANCELLED,
            retries = 0,
            elapsedMillis = 15
        )

        assertEquals(
            "metric=download_task operationId=download-0123456789abcdef0123456789abcdef " +
                "taskId=8 platform=uc outcome=cancelled retries=0 elapsedMs=15",
            metric
        )
        assertFalse(metric.contains("failureKind"))
    }
}
