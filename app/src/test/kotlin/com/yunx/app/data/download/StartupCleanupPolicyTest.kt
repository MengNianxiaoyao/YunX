package com.yunx.app.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupCleanupPolicyTest {
    @Test
    fun boundsPendingCleanupBatch() {
        assertEquals(1, StartupCleanupPolicy.pendingLimit(0))
        assertEquals(10, StartupCleanupPolicy.pendingLimit(10))
        assertEquals(StartupCleanupPolicy.MAX_PENDING_CLEANUPS, StartupCleanupPolicy.pendingLimit(999))
    }

    @Test
    fun boundsFallbackSweepByPagesAndDirectories() {
        assertTrue(StartupCleanupPolicy.shouldScanNextPage(1, 0))
        assertFalse(
            StartupCleanupPolicy.shouldScanNextPage(
                StartupCleanupPolicy.MAX_SWEEP_PAGES + 1,
                0
            )
        )
        assertFalse(
            StartupCleanupPolicy.shouldScanNextPage(
                1,
                StartupCleanupPolicy.MAX_SWEEP_DIRECTORIES
            )
        )
    }

    @Test
    fun cleanupTimeoutsFitWithinOverallBudget() {
        assertTrue(
            StartupCleanupPolicy.SINGLE_CLEANUP_TIMEOUT_MILLIS <
                StartupCleanupPolicy.CLEANUP_TIMEOUT_MILLIS
        )
    }
}
