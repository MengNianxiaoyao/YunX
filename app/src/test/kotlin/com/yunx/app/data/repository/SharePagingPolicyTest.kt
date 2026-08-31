package com.yunx.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharePagingPolicyTest {
    @Test
    fun continuesOnlyAfterFullPage() {
        assertEquals("2", SharePagingPolicy.nextPageCursor(1, 100, 100, 100))
        assertNull(SharePagingPolicy.nextPageCursor(1, 99, 100, 100))
        assertNull(SharePagingPolicy.nextPageCursor(1, 0, 100, 100))
    }

    @Test
    fun stopsAtMaximumPage() {
        assertNull(SharePagingPolicy.nextPageCursor(100, 100, 100, 100))
    }
}
