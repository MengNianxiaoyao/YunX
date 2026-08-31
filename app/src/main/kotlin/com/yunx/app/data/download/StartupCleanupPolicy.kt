package com.yunx.app.data.download

import com.yunx.app.data.network.NetworkClientPolicy

object StartupCleanupPolicy {
    const val MAX_PENDING_CLEANUPS = 20
    const val MAX_SWEEP_DIRECTORIES = 20
    const val MAX_SWEEP_PAGES = 20
    const val CLEANUP_TIMEOUT_MILLIS = 30_000L
    const val SINGLE_CLEANUP_TIMEOUT_MILLIS = NetworkClientPolicy.CLEANUP_CALL_TIMEOUT_MILLIS

    fun pendingLimit(requested: Int): Int = requested.coerceIn(1, MAX_PENDING_CLEANUPS)

    fun shouldScanNextPage(page: Int, collectedDirectories: Int): Boolean =
        page <= MAX_SWEEP_PAGES && collectedDirectories < MAX_SWEEP_DIRECTORIES
}
